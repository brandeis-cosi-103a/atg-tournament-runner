/**
 * Playback controller: loads tape.json, steps through events with animation.
 */
(function() {
  const params = new URLSearchParams(window.location.search);
  const tournamentName = params.get('t');
  if (!tournamentName) {
    document.getElementById('title').textContent = 'No tournament specified';
    return;
  }
  document.getElementById('title').textContent = tournamentName;

  // State
  let tape = null;
  let currentIndex = -1;
  let speed = 0;        // 0 = paused, negative = rewind, positive = forward
  let lastStepTime = 0;
  let celebrationShown = false;

  // Speed buttons (negative = rewind)
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
      speed = s.speed;
      speeds.forEach(function(x) {
        document.getElementById(x.id).classList.toggle('active', x.speed === speed);
      });
      // Just hide the celebration overlay, don't reset speed
      document.getElementById('celebration').classList.add('hidden');
      document.getElementById('confetti').innerHTML = '';
    });
  });

  // Timeline click
  document.getElementById('timeline').addEventListener('click', function(e) {
    if (!tape) return;
    const rect = this.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    const idx = Math.min(Math.max(Math.floor(pct * tape.events.length), 0), tape.events.length - 1);
    goToEvent(idx);
    hideCelebration();
  });

  // Load data
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

  function initPlayback() {
    BarChart.init(document.getElementById('chart-area'), tape.players, 0);

    // Build initial points (all zero)
    var pts = {};
    tape.players.forEach(function(p) { pts[p.id] = 0; });
    BarChart.update(pts, null);

    // Place round markers on timeline
    placeRoundMarkers();

    // Start at the beginning
    currentIndex = -1;
    celebrationShown = false;
    document.getElementById('timeline-progress').style.width = '0%';

    // Start animation loop
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
    if (speed !== 0 && tape) {
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
    celebrationShown = false; // Reset when rewinding
    renderEvent(currentIndex);
  }

  function goToEvent(idx) {
    currentIndex = idx;
    if (idx < tape.events.length - 1) {
      celebrationShown = false; // Reset when not at end
    }
    renderEvent(idx);
  }

  function formatCardName(name) {
    // Convert "SPRINT_PLANNING" to "Sprint Planning"
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

    // Update timeline progress
    var pct = ((idx + 1) / tape.events.length) * 100;
    document.getElementById('timeline-progress').style.width = pct + '%';

    // Update info overlay
    document.getElementById('info-round').textContent = ev.round;
    document.getElementById('info-game').textContent = ev.game !== undefined ? ev.game : ev.seq;

    if (ev.kingdomCards) {
      var formattedCards = ev.kingdomCards.map(formatCardName);
      document.getElementById('info-kingdom').textContent = formattedCards.join(', ');
    }

    // All players who participated in any table this step
    var changedIds = ev.placements ? ev.placements.map(function(p) { return p.id; }) : null;

    // Update bars with cumulative points
    BarChart.update(ev.points, changedIds);
  }

  // Click celebration to dismiss
  document.getElementById('celebration').addEventListener('click', function() {
    hideCelebration();
  });

  function showCelebration() {
    if (!tape || tape.events.length === 0) return;

    // Get final points from last event
    var finalEvent = tape.events[tape.events.length - 1];
    var pts = finalEvent.points;

    // Sort players by points descending
    var ranked = tape.players.slice().sort(function(a, b) {
      return (pts[b.id] || 0) - (pts[a.id] || 0);
    });

    // Populate podium
    if (ranked[0]) {
      document.getElementById('place-1-name').textContent = ranked[0].name;
      document.getElementById('place-1-points').textContent = pts[ranked[0].id] + ' pts';
    }
    if (ranked[1]) {
      document.getElementById('place-2-name').textContent = ranked[1].name;
      document.getElementById('place-2-points').textContent = pts[ranked[1].id] + ' pts';
    }
    if (ranked[2]) {
      document.getElementById('place-3-name').textContent = ranked[2].name;
      document.getElementById('place-3-points').textContent = pts[ranked[2].id] + ' pts';
    }

    document.getElementById('celebration').classList.remove('hidden');
    createConfetti();
  }

  function hideCelebration() {
    document.getElementById('celebration').classList.add('hidden');
    // Clear confetti
    document.getElementById('confetti').innerHTML = '';
    // Pause playback
    speed = 0;
    speeds.forEach(function(x) {
      document.getElementById(x.id).classList.toggle('active', x.speed === 0);
    });
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
})();
