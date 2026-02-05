/**
 * Playback controller: supports both live streaming and replay modes.
 * - Live mode: receives events via WebSocket, controls disabled
 * - Replay mode: loads tape.json, full playback controls
 */
(function() {
  const params = new URLSearchParams(window.location.search);
  const tournamentName = params.get('t');
  const tournamentId = params.get('id');
  const playersParam = params.get('players');

  if (!tournamentName) {
    document.getElementById('title').textContent = 'No tournament specified';
    return;
  }
  document.getElementById('title').textContent = tournamentName;

  // Set up download button
  var downloadBtn = document.getElementById('download-btn');
  downloadBtn.href = '/api/tournaments/' + encodeURIComponent(tournamentName) + '/download.zip';
  downloadBtn.classList.remove('hidden');

  // State
  let tape = null;
  let currentIndex = -1;
  let speed = 0;
  let lastStepTime = 0;
  let celebrationShown = false;
  let isLiveMode = !!tournamentId;
  let liveEvents = [];
  let livePlayers = [];
  let stompClient = null;
  let initialRating = 0;

  // Speed buttons
  const speeds = [
    { id: 'btn-r50x', speed: -50 },
    { id: 'btn-r10x', speed: -10 },
    { id: 'btn-r1x',  speed: -1 },
    { id: 'btn-pause', speed: 0 },
    { id: 'btn-1x',   speed: 1 },
    { id: 'btn-10x',  speed: 10 },
    { id: 'btn-50x',  speed: 50 }
  ];

  speeds.forEach(function(s) {
    document.getElementById(s.id).addEventListener('click', function() {
      if (isLiveMode) return; // Disabled during live
      speed = s.speed;
      speeds.forEach(function(x) {
        document.getElementById(x.id).classList.toggle('active', x.speed === speed);
      });
      document.getElementById('celebration').classList.add('hidden');
      document.getElementById('confetti').innerHTML = '';
    });
  });

  // Timeline click
  document.getElementById('timeline').addEventListener('click', function(e) {
    if (isLiveMode || !tape) return; // Disabled during live
    const rect = this.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    const idx = Math.min(Math.max(Math.floor(pct * tape.events.length), 0), tape.events.length - 1);
    goToEvent(idx);
    hideCelebration();
  });

  // Click celebration to reveal results, then dismiss
  document.getElementById('celebration').addEventListener('click', function() {
    var celebration = document.getElementById('celebration');
    if (!celebration.classList.contains('revealed')) {
      // First click: reveal results and start confetti
      celebration.classList.add('revealed');
      createConfetti();
    } else {
      // Second click: dismiss
      hideCelebration();
    }
  });

  // Start in appropriate mode
  if (isLiveMode) {
    startLiveMode();
  } else {
    startReplayMode();
  }

  /**
   * Live mode: connect to WebSocket, receive updates in real-time
   */
  function startLiveMode() {
    // Parse players from URL or fetch from status
    if (playersParam) {
      try {
        livePlayers = JSON.parse(decodeURIComponent(playersParam));
      } catch (e) {
        livePlayers = [];
      }
    }

    // Disable controls visually
    setControlsEnabled(false);

    // Show loading state
    document.getElementById('info-round').textContent = '-';
    document.getElementById('info-game').textContent = 'Connecting...';
    document.getElementById('info-kingdom').textContent = '-';

    // Connect to WebSocket
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function() {
      stompClient.subscribe('/topic/tournaments/' + tournamentId, function(message) {
        handleLiveUpdate(JSON.parse(message.body));
      });
    }, function(error) {
      console.error('WebSocket error:', error);
      document.getElementById('info-game').textContent = 'Connection lost';
    });

    // Initialize chart if we have players
    if (livePlayers.length > 0) {
      BarChart.init(document.getElementById('chart-area'), livePlayers, initialRating);
      var ratings = {};
      livePlayers.forEach(function(p) { ratings[p.id] = initialRating; });
      BarChart.update(ratings, null);
    }
  }

  /**
   * Handle a live tournament status update
   */
  function handleLiveUpdate(status) {
    var state = status.state;
    var ratings = status.ratings;
    var currentRound = status.currentRound;
    var totalRounds = status.totalRounds;
    var completedGames = status.completedGames;
    var totalGames = status.totalGames;

    // Initialize chart on first update with ratings (if not already done)
    if (ratings && Object.keys(ratings).length > 0 && livePlayers.length === 0) {
      livePlayers = Object.keys(ratings).map(function(id) {
        return { id: id, name: id };
      });
      BarChart.init(document.getElementById('chart-area'), livePlayers, initialRating);
    }

    if (state === 'QUEUED') {
      document.getElementById('info-round').textContent = '-';
      document.getElementById('info-game').textContent = 'Queued...';
      updateLiveTimeline(0);
    } else if (state === 'RUNNING') {
      document.getElementById('info-round').textContent = currentRound + ' / ' + totalRounds;
      document.getElementById('info-game').textContent = completedGames + ' / ' + totalGames;
      updateLiveTimeline(totalGames > 0 ? completedGames / totalGames : 0);
      if (ratings) {
        BarChart.update(ratings, null);
      }
    } else if (state === 'COMPLETED') {
      document.getElementById('info-round').textContent = totalRounds;
      document.getElementById('info-game').textContent = 'Complete!';
      updateLiveTimeline(1);
      if (ratings) {
        BarChart.update(ratings, null);
      }
      if (stompClient) {
        stompClient.disconnect();
      }
      // Transition to replay mode
      transitionToReplayMode();
    } else if (state === 'FAILED') {
      document.getElementById('info-game').textContent = 'Failed: ' + (status.error || 'Unknown error');
      if (stompClient) {
        stompClient.disconnect();
      }
    }
  }

  /**
   * Update timeline progress during live mode
   */
  function updateLiveTimeline(pct) {
    document.getElementById('timeline-progress').style.width = (pct * 100) + '%';
  }

  /**
   * Transition from live to replay mode after tournament completes
   */
  function transitionToReplayMode() {
    isLiveMode = false;

    // Load tape.json for replay
    fetch('/api/tournaments/' + encodeURIComponent(tournamentName) + '/tape.json')
      .then(function(res) {
        if (!res.ok) throw new Error('Failed to load tape');
        return res.json();
      })
      .then(function(data) {
        tape = data;
        enableReplayMode();
      })
      .catch(function(err) {
        console.error('Failed to load tape for replay:', err);
        // Still enable controls, just won't have replay
        setControlsEnabled(true);
      });
  }

  /**
   * Enable replay mode with full controls
   */
  function enableReplayMode() {
    setControlsEnabled(true);

    // Re-init chart with tape data
    initialRating = tape.scoring && tape.scoring.initial ? tape.scoring.initial : 0;
    BarChart.init(document.getElementById('chart-area'), tape.players, initialRating);

    // Build initial ratings
    var ratings = {};
    tape.players.forEach(function(p) { ratings[p.id] = initialRating; });
    BarChart.update(ratings, null);

    // Place round markers
    placeRoundMarkers();

    // Jump to end
    currentIndex = tape.events.length - 1;
    renderEvent(currentIndex);

    // Show celebration
    showCelebration();
  }

  /**
   * Replay mode: load tape.json and enable full controls
   */
  function startReplayMode() {
    fetch('/api/tournaments/' + encodeURIComponent(tournamentName) + '/tape.json')
      .then(function(res) {
        if (!res.ok) throw new Error('Failed to load tape');
        return res.json();
      })
      .then(function(data) {
        tape = data;
        initPlayback();
      })
      .catch(function(err) {
        document.getElementById('title').textContent = 'Error: ' + err.message;
      });
  }

  function initPlayback() {
    initialRating = tape.scoring && tape.scoring.initial ? tape.scoring.initial : 0;
    BarChart.init(document.getElementById('chart-area'), tape.players, initialRating);

    placeRoundMarkers();

    // Jump to end and show final state immediately for completed tournaments
    currentIndex = tape.events.length - 1;
    celebrationShown = true;
    renderEvent(currentIndex);
    renderStatsTable();

    requestAnimationFrame(tick);
  }

  function placeRoundMarkers() {
    var markers = document.getElementById('timeline-markers');
    markers.innerHTML = '';
    var total = tape.events.length;
    if (total === 0) return;
    var seenRounds = {};
    tape.events.forEach(function(ev, i) {
      if (!seenRounds[ev.round]) {
        seenRounds[ev.round] = true;
        var marker = document.createElement('div');
        marker.className = 'round-marker';
        marker.style.left = ((i / total) * 100) + '%';
        marker.title = 'Round ' + ev.round;
        markers.appendChild(marker);
      }
    });
  }

  function tick(timestamp) {
    if (!isLiveMode && speed !== 0 && tape) {
      var interval = 1000 / Math.abs(speed);
      if (timestamp - lastStepTime >= interval) {
        lastStepTime = timestamp;
        if (speed > 0) {
          stepForward();
        } else {
          stepBackward();
        }
      }
    }
    requestAnimationFrame(tick);
  }

  function stepForward() {
    if (currentIndex >= tape.events.length - 1) {
      if (!celebrationShown) {
        showCelebration();
        celebrationShown = true;
      }
      return;
    }
    currentIndex++;
    renderEvent(currentIndex);
  }

  function stepBackward() {
    if (currentIndex <= 0) return;
    currentIndex--;
    celebrationShown = false;
    renderEvent(currentIndex);
  }

  function goToEvent(idx) {
    currentIndex = idx;
    if (idx < tape.events.length - 1) {
      celebrationShown = false;
    }
    renderEvent(idx);
  }

  function formatCardName(name) {
    return name
      .toLowerCase()
      .split('_')
      .map(function(word) {
        return word.charAt(0).toUpperCase() + word.slice(1);
      })
      .join(' ');
  }

  function renderEvent(idx) {
    var ev = tape.events[idx];
    if (!ev) return;

    var pct = ((idx + 1) / tape.events.length) * 100;
    document.getElementById('timeline-progress').style.width = pct + '%';

    document.getElementById('info-round').textContent = ev.round;
    var eventsInRound = (ev.gamesInRound || 1) * (ev.tables || 1);
    var eventInRound = (ev.game || 0) * (ev.tables || 1) + (ev.table || 1);
    document.getElementById('info-game').textContent = eventInRound + ' / ' + eventsInRound;

    if (ev.kingdomCards) {
      var formattedCards = ev.kingdomCards.map(formatCardName);
      document.getElementById('info-kingdom').textContent = formattedCards.join(', ');
    }

    var changedIds = ev.placements ? ev.placements.map(function(p) { return p.id; }) : null;
    BarChart.update(ev.ratings, changedIds);
  }

  function setControlsEnabled(enabled) {
    speeds.forEach(function(s) {
      var btn = document.getElementById(s.id);
      btn.disabled = !enabled;
      if (!enabled) {
        btn.classList.add('disabled');
      } else {
        btn.classList.remove('disabled');
      }
    });

    var timeline = document.getElementById('timeline');
    if (!enabled) {
      timeline.classList.add('disabled');
    } else {
      timeline.classList.remove('disabled');
    }
  }

  function showCelebration() {
    if (!tape || tape.events.length === 0) return;

    var finalEvent = tape.events[tape.events.length - 1];
    var ratings = finalEvent.ratings;

    var ranked = tape.players.slice().sort(function(a, b) {
      return (ratings[b.id] || 0) - (ratings[a.id] || 0);
    });

    if (ranked[0]) {
      document.getElementById('place-1-name').textContent = ranked[0].name;
      document.getElementById('place-1-points').textContent = ratings[ranked[0].id].toFixed(1);
    }
    if (ranked[1]) {
      document.getElementById('place-2-name').textContent = ranked[1].name;
      document.getElementById('place-2-points').textContent = ratings[ranked[1].id].toFixed(1);
    }
    if (ranked[2]) {
      document.getElementById('place-3-name').textContent = ranked[2].name;
      document.getElementById('place-3-points').textContent = ratings[ranked[2].id].toFixed(1);
    }

    var celebration = document.getElementById('celebration');
    celebration.classList.remove('hidden');
    celebration.classList.remove('revealed');
  }

  function hideCelebration() {
    var celebration = document.getElementById('celebration');
    celebration.classList.add('hidden');
    celebration.classList.remove('revealed');
    document.getElementById('confetti').innerHTML = '';
    if (!isLiveMode) {
      speed = 0;
      speeds.forEach(function(x) {
        document.getElementById(x.id).classList.toggle('active', x.speed === 0);
      });
    }
  }

  function createConfetti() {
    var container = document.getElementById('confetti');
    container.innerHTML = '';
    var colors = ['#ffd700', '#c0c0c0', '#cd7f32', '#4a90d9', '#e74c3c', '#2ecc71', '#9b59b6'];
    var shapes = ['square', 'circle'];

    for (var i = 0; i < 100; i++) {
      var confetti = document.createElement('div');
      confetti.className = 'confetti';
      confetti.style.left = Math.random() * 100 + '%';
      confetti.style.backgroundColor = colors[Math.floor(Math.random() * colors.length)];
      confetti.style.animationDuration = (2 + Math.random() * 3) + 's';
      confetti.style.animationDelay = Math.random() * 2 + 's';
      if (shapes[Math.floor(Math.random() * shapes.length)] === 'circle') {
        confetti.style.borderRadius = '50%';
      }
      container.appendChild(confetti);
    }
  }

  // Main page stats toggle
  document.getElementById('main-stats-toggle').addEventListener('click', function() {
    var toggle = document.getElementById('main-stats-toggle');
    var panel = document.getElementById('main-stats-panel');
    toggle.classList.toggle('collapsed');
    panel.classList.toggle('hidden');
  });

  /**
   * Calculate tournament statistics from tape data
   */
  function calculateStats() {
    if (!tape || !tape.events || tape.events.length === 0) return null;

    var stats = {};
    var playerMap = {};

    // Initialize player data
    tape.players.forEach(function(p) {
      playerMap[p.id] = p.name;
      stats[p.id] = {
        id: p.id,
        name: p.name,
        games: 0,
        places: { 1: 0, 2: 0, 3: 0, 4: 0 },
        scores: [],     // All scores for stdev calculation
        totalScore: 0,
        bestScore: -Infinity,
        worstScore: Infinity,
        beatCount: {},  // Who this player beat (id -> count)
        lostCount: {},  // Who beat this player (id -> count)
        ratingHistory: []  // Rating progression: [{game, rating}, ...]
      };
    });

    // Track last known rating per player for history
    var lastRatings = {};
    tape.players.forEach(function(p) {
      lastRatings[p.id] = initialRating;
    });

    // Track game count per player
    var gameCount = {};
    tape.players.forEach(function(p) {
      gameCount[p.id] = 0;
    });

    // Process each game event
    tape.events.forEach(function(ev) {
      if (!ev.placements || ev.placements.length === 0) return;

      // Sort placements by score descending
      var sorted = ev.placements.slice().sort(function(a, b) {
        return b.score - a.score;
      });

      // Assign places (handle ties)
      var places = [];
      var currentPlace = 1;
      for (var i = 0; i < sorted.length; i++) {
        if (i > 0 && sorted[i].score < sorted[i - 1].score) {
          currentPlace = i + 1;
        }
        places.push({ id: sorted[i].id, score: sorted[i].score, place: currentPlace });
      }

      // Update stats for each player in this game
      places.forEach(function(p) {
        var s = stats[p.id];
        if (!s) return;

        s.games++;
        s.scores.push(p.score);
        s.totalScore += p.score;
        if (p.score > s.bestScore) s.bestScore = p.score;
        if (p.score < s.worstScore) s.worstScore = p.score;

        // Cap place at 4 for display purposes
        var placeKey = Math.min(p.place, 4);
        s.places[placeKey]++;
      });

      // Calculate head-to-head results
      for (var i = 0; i < places.length; i++) {
        for (var j = i + 1; j < places.length; j++) {
          var higher = places[i];
          var lower = places[j];

          // Only count if actually different scores (not a tie)
          if (higher.score > lower.score) {
            // higher beat lower
            if (!stats[higher.id].beatCount[lower.id]) {
              stats[higher.id].beatCount[lower.id] = 0;
            }
            stats[higher.id].beatCount[lower.id]++;

            if (!stats[lower.id].lostCount[higher.id]) {
              stats[lower.id].lostCount[higher.id] = 0;
            }
            stats[lower.id].lostCount[higher.id]++;
          }
        }
      }

      // Record rating history for players in this game
      if (ev.ratings) {
        places.forEach(function(p) {
          gameCount[p.id]++;
          var rating = ev.ratings[p.id];
          if (rating !== undefined) {
            stats[p.id].ratingHistory.push({ game: gameCount[p.id], rating: rating });
            lastRatings[p.id] = rating;
          }
        });
      }
    });

    // Get final ratings
    var finalEvent = tape.events[tape.events.length - 1];

    // Convert to array and add computed fields
    var result = Object.values(stats).map(function(s) {
      s.rating = finalEvent.ratings[s.id] || 0;
      s.avgScore = s.games > 0 ? (s.totalScore / s.games) : 0;

      // Calculate stdev
      if (s.games > 1) {
        var mean = s.avgScore;
        var sumSquaredDiff = s.scores.reduce(function(acc, score) {
          var diff = score - mean;
          return acc + diff * diff;
        }, 0);
        s.stdev = Math.sqrt(sumSquaredDiff / s.games);
      } else {
        s.stdev = 0;
      }

      // Fix best/worst for players with no games
      if (s.games === 0) {
        s.bestScore = 0;
        s.worstScore = 0;
      }

      // Find most beaten opponent (exclude self)
      var maxBeat = { id: null, count: 0, name: '' };
      Object.keys(s.beatCount).forEach(function(oppId) {
        if (oppId !== s.id && s.beatCount[oppId] > maxBeat.count) {
          maxBeat = { id: oppId, count: s.beatCount[oppId], name: playerMap[oppId] };
        }
      });
      s.mostBeaten = maxBeat.count > 0 ? maxBeat : null;

      // Find most lost to opponent (exclude self)
      var maxLost = { id: null, count: 0, name: '' };
      Object.keys(s.lostCount).forEach(function(oppId) {
        if (oppId !== s.id && s.lostCount[oppId] > maxLost.count) {
          maxLost = { id: oppId, count: s.lostCount[oppId], name: playerMap[oppId] };
        }
      });
      s.mostLostTo = maxLost.count > 0 ? maxLost : null;

      // Get all cards from deckStats (excluding starting cards) with avg per game
      var startingCards = ['BITCOIN', 'METHOD', 'BUG'];
      s.allCards = [];
      if (tape.deckStats && tape.deckStats[s.id]) {
        var cardCounts = tape.deckStats[s.id];
        s.allCards = Object.keys(cardCounts)
          .filter(function(card) { return startingCards.indexOf(card) === -1; })
          .map(function(card) {
            return {
              name: card,
              total: cardCounts[card],
              avgPerGame: s.games > 0 ? (cardCounts[card] / s.games) : 0
            };
          })
          .sort(function(a, b) { return b.avgPerGame - a.avgPerGame; });
      }

      return s;
    });

    // Sort by rating descending
    result.sort(function(a, b) { return b.rating - a.rating; });

    return result;
  }

  /**
   * Build an SVG sparkline from rating history
   * @param history - array of {game, rating} objects
   * @param width - SVG width
   * @param height - SVG height
   * @param globalMin - shared minimum for consistent scale across sparklines
   * @param globalMax - shared maximum for consistent scale across sparklines
   */
  function buildSparkline(history, width, height, globalMin, globalMax) {
    if (!history || history.length < 2) {
      return '<svg class="sparkline" width="' + width + '" height="' + height + '"></svg>';
    }

    var range = globalMax - globalMin;

    // Handle case where all values are the same
    if (range === 0) {
      var y = height / 2;
      return '<svg class="sparkline" width="' + width + '" height="' + height + '">' +
        '<line x1="0" y1="' + y + '" x2="' + width + '" y2="' + y + '" stroke="#4a90d9" stroke-width="1.5"/>' +
        '</svg>';
    }

    // Build polyline points
    var points = history.map(function(pt, i) {
      var x = (i / (history.length - 1)) * width;
      var y = height - ((pt.rating - globalMin) / range) * height;
      return x.toFixed(1) + ',' + y.toFixed(1);
    }).join(' ');

    // Build invisible hover circles with tooltips
    var circles = history.map(function(pt, i) {
      var x = (i / (history.length - 1)) * width;
      var y = height - ((pt.rating - globalMin) / range) * height;
      return '<circle cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="6" fill="transparent" class="sparkline-hover">' +
        '<title>Game ' + pt.game + ': ' + pt.rating.toFixed(1) + '</title></circle>';
    }).join('');

    // Final point dot
    var lastX = width;
    var lastY = height - ((history[history.length - 1].rating - globalMin) / range) * height;

    return '<svg class="sparkline" width="' + width + '" height="' + height + '" viewBox="0 0 ' + width + ' ' + height + '">' +
      '<polyline fill="none" stroke="#4a90d9" stroke-width="1.5" points="' + points + '"/>' +
      '<circle cx="' + lastX.toFixed(1) + '" cy="' + lastY.toFixed(1) + '" r="2" fill="#4a90d9"/>' +
      circles +
      '</svg>';
  }

  /**
   * Build the statistics HTML using player cards
   */
  function buildStatsTableHtml() {
    var stats = calculateStats();

    if (!stats || stats.length === 0) {
      return '<p style="color: #8899a6; text-align: center;">No statistics available</p>';
    }

    // Calculate global min/max for sparklines
    // Drop first 10% of each player's history to avoid initial rating jump
    var globalMin = Infinity;
    var globalMax = -Infinity;
    stats.forEach(function(s) {
      var skipCount = Math.ceil(s.ratingHistory.length * 0.1);
      var displayHistory = s.ratingHistory.slice(skipCount);
      if (displayHistory.length > 0) {
        displayHistory.forEach(function(pt) {
          if (pt.rating < globalMin) globalMin = pt.rating;
          if (pt.rating > globalMax) globalMax = pt.rating;
        });
      }
    });
    // Add padding for visual comfort
    var range = globalMax - globalMin;
    var padding = range * 0.1;
    globalMin -= padding;
    globalMax += padding;

    var html = '<div class="player-cards">';

    stats.forEach(function(s, idx) {
      var rank = idx + 1;
      var medalClass = rank <= 3 ? 'medal-' + rank : '';

      html += '<div class="player-card ' + medalClass + '">';

      // Header with rank, name, rating
      html += '<div class="player-card-header">';
      html += '<span class="player-rank">#' + rank + '</span>';
      html += '<span class="player-name">' + escapeHtml(s.name) + '</span>';
      html += '<span class="player-rating">' + s.rating.toFixed(1) + '</span>';
      html += '</div>';

      // Sparkline showing rating progression (drop first 10% to avoid initial jump)
      var skipCount = Math.ceil(s.ratingHistory.length * 0.1);
      var displayHistory = s.ratingHistory.slice(skipCount);
      html += '<div class="player-sparkline">';
      html += buildSparkline(displayHistory, 280, 24, globalMin, globalMax);
      html += '</div>';

      // Stats row: games and placements
      html += '<div class="player-card-stats">';
      html += '<div class="stat-group">';
      html += '<span class="stat-label">Games</span>';
      html += '<span class="stat-value">' + s.games + '</span>';
      html += '</div>';
      html += '<div class="stat-group placements">';
      html += '<span class="stat-label">Finishes</span>';
      html += '<span class="stat-value">';
      html += '<span class="place-1st">' + s.places[1] + '</span>';
      html += '<span class="place-2nd">' + s.places[2] + '</span>';
      html += '<span class="place-3rd">' + s.places[3] + '</span>';
      html += '<span class="place-4th">' + s.places[4] + '</span>';
      html += '</span>';
      html += '</div>';
      html += '</div>';

      // Rivalries section
      html += '<div class="player-card-section">';
      html += '<div class="section-title">Rivalries</div>';
      if (s.mostBeaten) {
        html += '<div class="rivalry-row">';
        html += '<span class="rivalry-label">Beats most often:</span>';
        html += '<span class="rivalry-value rivalry-win">' + escapeHtml(s.mostBeaten.name) + ' (' + s.mostBeaten.count + ' times)</span>';
        html += '</div>';
      }
      if (s.mostLostTo) {
        html += '<div class="rivalry-row">';
        html += '<span class="rivalry-label">Beaten most often by:</span>';
        html += '<span class="rivalry-value rivalry-loss">' + escapeHtml(s.mostLostTo.name) + ' (' + s.mostLostTo.count + ' times)</span>';
        html += '</div>';
      }
      if (!s.mostBeaten && !s.mostLostTo) {
        html += '<div class="no-data">No rivalry data</div>';
      }
      html += '</div>';

      // Cards section
      html += '<div class="player-card-section">';
      html += '<div class="section-title">Card Preferences (avg per game)</div>';
      if (s.allCards.length > 0) {
        html += '<div class="card-list">';
        s.allCards.forEach(function(card) {
          html += '<div class="card-row">';
          html += '<span class="card-name">' + formatCardName(card.name) + '</span>';
          html += '<span class="card-avg">' + card.avgPerGame.toFixed(2) + '</span>';
          html += '</div>';
        });
        html += '</div>';
      } else {
        html += '<div class="no-data">No card data available</div>';
      }
      html += '</div>';

      html += '</div>'; // end player-card
    });

    html += '</div>'; // end player-cards
    return html;
  }

  /**
   * Render the statistics table to main page
   */
  function renderStatsTable() {
    var html = buildStatsTableHtml();
    document.getElementById('main-stats-table-container').innerHTML = html;
    document.getElementById('main-stats-section').classList.remove('hidden');
  }

  function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Render stats when celebration is shown
  var origShowCelebration = showCelebration;
  showCelebration = function() {
    origShowCelebration();
    renderStatsTable();
  };

  // Start animation loop
  requestAnimationFrame(tick);
})();
