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

    var ratings = {};
    tape.players.forEach(function(p) { ratings[p.id] = initialRating; });
    BarChart.update(ratings, null);

    placeRoundMarkers();

    currentIndex = -1;
    celebrationShown = false;
    document.getElementById('timeline-progress').style.width = '0%';

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

  // Stats panel toggle
  document.getElementById('stats-toggle').addEventListener('click', function(e) {
    e.stopPropagation(); // Don't dismiss celebration
    var toggle = document.getElementById('stats-toggle');
    var panel = document.getElementById('stats-panel');
    toggle.classList.toggle('expanded');
    panel.classList.toggle('hidden');
  });

  // Prevent clicks inside stats panel from dismissing celebration
  document.getElementById('stats-panel').addEventListener('click', function(e) {
    e.stopPropagation();
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
        lostCount: {}   // Who beat this player (id -> count)
      };
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

      // Find most beaten opponent
      var maxBeat = { id: null, count: 0, name: '' };
      Object.keys(s.beatCount).forEach(function(oppId) {
        if (s.beatCount[oppId] > maxBeat.count) {
          maxBeat = { id: oppId, count: s.beatCount[oppId], name: playerMap[oppId] };
        }
      });
      s.mostBeaten = maxBeat.count > 0 ? maxBeat : null;

      // Find most lost to opponent
      var maxLost = { id: null, count: 0, name: '' };
      Object.keys(s.lostCount).forEach(function(oppId) {
        if (s.lostCount[oppId] > maxLost.count) {
          maxLost = { id: oppId, count: s.lostCount[oppId], name: playerMap[oppId] };
        }
      });
      s.mostLostTo = maxLost.count > 0 ? maxLost : null;

      return s;
    });

    // Sort by rating descending
    result.sort(function(a, b) { return b.rating - a.rating; });

    return result;
  }

  /**
   * Render the statistics table
   */
  function renderStatsTable() {
    var container = document.getElementById('stats-table-container');
    var stats = calculateStats();

    if (!stats || stats.length === 0) {
      container.innerHTML = '<p style="color: #8899a6; text-align: center;">No statistics available</p>';
      return;
    }

    var html = '<table class="stats-table">';
    html += '<thead><tr>';
    html += '<th class="rank-cell">#</th>';
    html += '<th>Player</th>';
    html += '<th>Rating</th>';
    html += '<th>Games</th>';
    html += '<th>Places</th>';
    html += '<th>Score</th>';
    html += '<th>Rivalries</th>';
    html += '</tr></thead>';
    html += '<tbody>';

    stats.forEach(function(s, idx) {
      html += '<tr>';
      html += '<td class="rank-cell">' + (idx + 1) + '</td>';
      html += '<td class="name-cell" title="' + escapeHtml(s.name) + '">' + escapeHtml(s.name) + '</td>';
      html += '<td class="rating-cell">' + s.rating.toFixed(1) + '</td>';
      html += '<td class="games-cell">' + s.games + '</td>';
      html += '<td class="places-cell">';
      if (s.places[1] > 0) html += '<span class="place-badge place-1">' + s.places[1] + '×1st</span>';
      if (s.places[2] > 0) html += '<span class="place-badge place-2">' + s.places[2] + '×2nd</span>';
      if (s.places[3] > 0) html += '<span class="place-badge place-3">' + s.places[3] + '×3rd</span>';
      if (s.places[4] > 0) html += '<span class="place-badge place-4">' + s.places[4] + '×4th+</span>';
      html += '</td>';
      html += '<td class="score-cell">';
      html += '<span class="score-avg">' + s.avgScore.toFixed(1) + '</span>';
      html += '<span class="score-range">±' + s.stdev.toFixed(1) + '</span>';
      html += '<span class="score-minmax">' + s.worstScore + '–' + s.bestScore + '</span>';
      html += '</td>';
      html += '<td class="rivalry-cell">';
      if (s.mostBeaten) {
        html += '<span class="rivalry-item rivalry-beat">▲ ' + escapeHtml(s.mostBeaten.name) + ' (' + s.mostBeaten.count + ')</span>';
      }
      if (s.mostLostTo) {
        html += '<span class="rivalry-item rivalry-lost">▼ ' + escapeHtml(s.mostLostTo.name) + ' (' + s.mostLostTo.count + ')</span>';
      }
      html += '</td>';
      html += '</tr>';
    });

    html += '</tbody></table>';
    container.innerHTML = html;
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
