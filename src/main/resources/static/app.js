(function() {
  'use strict';

  // Built-in strategy options
  const BUILT_IN_STRATEGIES = [
    { value: 'naive-money', label: 'Naive Money' },
    { value: 'action-heavy', label: 'Action Heavy' },
    { value: 'random', label: 'Random' }
  ];

  // State
  let playerCount = 0;
  let stompClient = null;
  let currentTournamentId = null;
  let currentTournamentName = null;
  let previousRatings = {};

  // DOM elements
  const form = document.getElementById('tournament-form');
  const playersList = document.getElementById('players-list');
  const addPlayerBtn = document.getElementById('add-player-btn');
  const progressContainer = document.getElementById('progress-container');
  const progressFill = document.getElementById('progress-fill');
  const progressInfo = document.getElementById('progress-info');
  const errorMessage = document.getElementById('error-message');
  const successMessage = document.getElementById('success-message');
  const submitBtn = document.getElementById('submit-btn');

  // Initialize with 4 players
  function init() {
    addPlayer();
    addPlayer();
    addPlayer();
    addPlayer();

    addPlayerBtn.addEventListener('click', addPlayer);
    form.addEventListener('submit', handleSubmit);
  }

  // Add a player to the form
  function addPlayer() {
    playerCount++;
    const playerId = `player-${playerCount}`;

    const playerDiv = document.createElement('div');
    playerDiv.className = 'player-item';
    playerDiv.dataset.playerId = playerId;

    playerDiv.innerHTML = `
      <div class="player-header">
        <span class="player-number">Player ${playerCount}</span>
        <button type="button" class="btn btn-danger" onclick="removePlayer('${playerId}')">Remove</button>
      </div>
      <div class="form-group">
        <label class="form-label" for="${playerId}-name">Name</label>
        <input type="text" id="${playerId}-name" class="form-input" required placeholder="Player Name">
      </div>
      <div class="form-group">
        <label class="form-label" for="${playerId}-type">Type</label>
        <select id="${playerId}-type" class="select" onchange="togglePlayerUrl('${playerId}')">
          <option value="url">URL (Remote Player)</option>
          <option value="built-in">Built-in Strategy</option>
        </select>
      </div>
      <div class="form-group" id="${playerId}-url-group">
        <label class="form-label" for="${playerId}-url">Player URL</label>
        <input type="url" id="${playerId}-url" class="form-input" required placeholder="http://localhost:8080">
      </div>
      <div class="form-group" id="${playerId}-strategy-group" style="display: none;">
        <label class="form-label" for="${playerId}-strategy">Strategy</label>
        <select id="${playerId}-strategy" class="select">
          ${BUILT_IN_STRATEGIES.map(s => `<option value="${s.value}">${s.label}</option>`).join('')}
        </select>
      </div>
    `;

    playersList.appendChild(playerDiv);
  }

  // Remove a player from the form
  window.removePlayer = function(playerId) {
    const playerDiv = document.querySelector(`[data-player-id="${playerId}"]`);
    if (playerDiv) {
      playerDiv.remove();
      renumberPlayers();
    }
  };

  // Renumber players after removal
  function renumberPlayers() {
    const players = playersList.querySelectorAll('.player-item');
    players.forEach((player, index) => {
      const numberSpan = player.querySelector('.player-number');
      if (numberSpan) {
        numberSpan.textContent = `Player ${index + 1}`;
      }
    });
  }

  // Toggle between URL and built-in strategy
  window.togglePlayerUrl = function(playerId) {
    const typeSelect = document.getElementById(`${playerId}-type`);
    const urlGroup = document.getElementById(`${playerId}-url-group`);
    const strategyGroup = document.getElementById(`${playerId}-strategy-group`);
    const urlInput = document.getElementById(`${playerId}-url`);
    const strategySelect = document.getElementById(`${playerId}-strategy`);

    if (typeSelect.value === 'url') {
      urlGroup.style.display = 'block';
      strategyGroup.style.display = 'none';
      urlInput.required = true;
      strategySelect.required = false;
    } else {
      urlGroup.style.display = 'none';
      strategyGroup.style.display = 'block';
      urlInput.required = false;
      strategySelect.required = true;
    }
  };

  // Handle form submission
  async function handleSubmit(e) {
    e.preventDefault();

    // Hide previous messages
    errorMessage.style.display = 'none';
    successMessage.style.display = 'none';

    // Collect form data
    const tournamentName = document.getElementById('tournament-name').value.trim();
    const rounds = parseInt(document.getElementById('rounds').value);
    const gamesPerPlayer = parseInt(document.getElementById('games-per-player').value);

    // Collect players
    const players = [];
    const playerItems = playersList.querySelectorAll('.player-item');

    playerItems.forEach((item) => {
      const playerId = item.dataset.playerId;
      const name = document.getElementById(`${playerId}-name`).value.trim();
      const type = document.getElementById(`${playerId}-type`).value;

      let url;
      if (type === 'url') {
        url = document.getElementById(`${playerId}-url`).value.trim();
      } else {
        const strategy = document.getElementById(`${playerId}-strategy`).value;
        url = `built-in:${strategy}`;
      }

      players.push({ name, url });
    });

    // Validate
    if (players.length < 4) {
      showError('At least 4 players are required');
      return;
    }

    if (!/^[a-z0-9-]+$/.test(tournamentName)) {
      showError('Tournament name must contain only lowercase letters, numbers, and hyphens');
      return;
    }

    // Prepare request
    const request = {
      tournamentName,
      rounds,
      gamesPerPlayer,
      players
    };

    // Disable form
    submitBtn.disabled = true;
    submitBtn.textContent = 'Starting Tournament...';

    try {
      // Submit tournament
      const response = await fetch('/api/tournaments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
      });

      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.error || 'Failed to start tournament');
      }

      // Store tournament info
      currentTournamentId = result.tournamentId;
      currentTournamentName = tournamentName;

      // Show progress container
      progressContainer.classList.add('active');

      // Connect to WebSocket for progress updates
      connectWebSocket(currentTournamentId);

    } catch (error) {
      showError(error.message);
      submitBtn.disabled = false;
      submitBtn.textContent = 'Run Tournament';
    }
  }

  // Connect to WebSocket
  function connectWebSocket(tournamentId) {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // Disable debug logging
    stompClient.debug = null;

    stompClient.connect({}, function() {
      console.log('WebSocket connected');

      // Subscribe to tournament updates
      stompClient.subscribe(`/topic/tournaments/${tournamentId}`, function(message) {
        const status = JSON.parse(message.body);
        updateProgress(status);
      });
    }, function(error) {
      console.error('WebSocket connection error:', error);
      showError('Lost connection to server. Progress updates may not be available.');
    });
  }

  // Update progress display
  function updateProgress(status) {
    const { state, currentRound, totalRounds, completedGames, totalGames, ratings, error } = status;

    if (state === 'QUEUED') {
      progressInfo.innerHTML = 'Tournament queued for execution...';
      updateProgressBar(0);
      hideRatings();
    } else if (state === 'RUNNING') {
      const roundProgress = totalRounds > 0 ? (currentRound / totalRounds) * 100 : 0;
      const gameProgress = totalGames > 0 ? (completedGames / totalGames) * 100 : 0;
      const overallProgress = (roundProgress + gameProgress) / 2;

      progressInfo.innerHTML = `
        Round ${currentRound} of ${totalRounds}<br>
        Games completed: ${completedGames} of ${totalGames}
      `;
      updateProgressBar(overallProgress);

      // Update ratings leaderboard
      if (ratings && Object.keys(ratings).length > 0) {
        updateRatingsLeaderboard(ratings);
      }
    } else if (state === 'COMPLETED') {
      progressInfo.innerHTML = 'Tournament completed successfully!';
      updateProgressBar(100);

      // Update final ratings
      if (ratings) {
        updateRatingsLeaderboard(ratings);
      }

      // Disconnect WebSocket
      if (stompClient) {
        stompClient.disconnect();
      }

      // Redirect to viewer after a short delay
      setTimeout(() => {
        window.location.href = `/playback.html?t=${encodeURIComponent(currentTournamentName)}`;
      }, 2000);
    } else if (state === 'FAILED') {
      progressInfo.innerHTML = `Tournament failed: ${error || 'Unknown error'}`;
      showError(error || 'Tournament execution failed');

      // Disconnect WebSocket
      if (stompClient) {
        stompClient.disconnect();
      }

      // Re-enable form
      submitBtn.disabled = false;
      submitBtn.textContent = 'Run Tournament';
    }
  }

  // Update progress bar
  function updateProgressBar(percentage) {
    const roundedPercentage = Math.round(percentage);
    progressFill.style.width = `${roundedPercentage}%`;
    progressFill.textContent = `${roundedPercentage}%`;
  }

  // Show error message
  function showError(message) {
    errorMessage.textContent = message;
    errorMessage.style.display = 'block';
  }

  // Show success message
  function showSuccess(message) {
    successMessage.textContent = message;
    successMessage.style.display = 'block';
  }

  // Update ratings leaderboard
  function updateRatingsLeaderboard(ratings) {
    const container = document.getElementById('ratings-container');
    const leaderboard = document.getElementById('ratings-leaderboard');

    // Show container if hidden
    if (container.style.display === 'none') {
      container.style.display = 'block';
    }

    // Sort players by rating (descending)
    const sorted = Object.entries(ratings)
      .sort((a, b) => b[1] - a[1])
      .map(([id, rating], index) => ({
        rank: index + 1,
        playerId: id,
        rating: rating,
        change: previousRatings[id] ? rating - previousRatings[id] : 0
      }));

    // Build leaderboard HTML with change indicators
    let html = '<table class="leaderboard-table">';
    html += '<thead><tr><th>Rank</th><th>Player</th><th>Rating</th><th>Change</th></tr></thead>';
    html += '<tbody>';

    for (const player of sorted) {
      const changeClass = player.change > 0 ? 'positive' :
                         player.change < 0 ? 'negative' : 'neutral';
      const changeSymbol = player.change > 0 ? '↑' :
                          player.change < 0 ? '↓' : '−';
      const changeText = player.change !== 0 ?
        `${player.change > 0 ? '+' : ''}${player.change.toFixed(1)} ${changeSymbol}` :
        '−';

      html += `
        <tr>
          <td class="rank">${player.rank}</td>
          <td class="player-name">${player.playerId}</td>
          <td class="rating">${player.rating.toFixed(1)}</td>
          <td class="change ${changeClass}">${changeText}</td>
        </tr>
      `;
    }

    html += '</tbody></table>';
    leaderboard.innerHTML = html;

    // Save current ratings for next update
    previousRatings = {...ratings};
  }

  // Hide ratings leaderboard
  function hideRatings() {
    const container = document.getElementById('ratings-container');
    container.style.display = 'none';
    previousRatings = {};
  }

  // Initialize on page load
  init();
})();
