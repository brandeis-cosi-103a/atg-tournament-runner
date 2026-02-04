(function() {
  'use strict';

  // Player type options - populated from discovered players
  let PLAYER_TYPES = [
    { value: 'url', label: 'URL (Network Player)', description: '' }
  ];

  // State
  let playerCount = 0;
  let showDelayOption = false;

  // DOM elements
  const form = document.getElementById('tournament-form');
  const playersTbody = document.getElementById('players-tbody');
  const addPlayerBtn = document.getElementById('add-player-btn');
  const errorMessage = document.getElementById('error-message');
  const submitBtn = document.getElementById('submit-btn');

  // Initialize
  async function init() {
    // Fetch config to check for optional features and discovered players
    try {
      const response = await fetch('/api/tournaments/config');
      if (response.ok) {
        const config = await response.json();
        showDelayOption = config.showDelayOption || false;

        // Build player types from discovered players
        if (config.discoveredPlayers && config.discoveredPlayers.length > 0) {
          PLAYER_TYPES = config.discoveredPlayers
            .filter(p => p.hasZeroArgConstructor || p.hasNameConstructor)
            .map(p => ({
              value: 'classpath:' + p.className,
              label: p.displayName,
              description: p.description || ''
            }));
          // Always add URL option at the end
          PLAYER_TYPES.push({ value: 'url', label: 'URL (Network Player)', description: '' });
        }
      }
    } catch (e) {
      // Ignore config fetch errors, use defaults
      console.warn('Config fetch failed, using defaults:', e);
    }

    // Add delay column header if enabled
    if (showDelayOption) {
      const headerRow = document.querySelector('#players-table thead tr');
      const delayTh = document.createElement('th');
      delayTh.textContent = 'Delay';
      headerRow.insertBefore(delayTh, headerRow.lastElementChild);
    }

    for (let i = 0; i < 4; i++) addPlayer();
    addPlayerBtn.addEventListener('click', addPlayer);
    form.addEventListener('submit', handleSubmit);
  }

  // Add a player row
  function addPlayer() {
    playerCount++;
    const rowNum = playersTbody.children.length + 1;
    const playerId = `player-${playerCount}`;

    const tr = document.createElement('tr');
    tr.dataset.playerId = playerId;

    // Get default selection (first player type)
    const defaultValue = PLAYER_TYPES.length > 0 ? PLAYER_TYPES[0].value : 'url';

    const typeOptions = PLAYER_TYPES.map((t, i) => {
      const title = t.description ? ` title="${escapeHtml(t.description)}"` : '';
      return `<option value="${t.value}"${i === 0 ? ' selected' : ''}${title}>${t.label}</option>`;
    }).join('');

    let html = `
      <td class="row-num">${rowNum}</td>
      <td><input type="text" class="player-name-input" data-field="name" required value="Player${rowNum}"></td>
      <td><select class="player-type-select" data-field="type">${typeOptions}</select></td>
      <td><input type="url" class="player-url-input" data-field="url" placeholder="http://..." disabled></td>
    `;

    if (showDelayOption) {
      html += `<td><input type="checkbox" class="player-delay-checkbox" data-field="delay"></td>`;
    }

    html += `<td><button type="button" class="btn btn-sm btn-danger" onclick="removePlayer('${playerId}')">X</button></td>`;

    tr.innerHTML = html;

    // Set up type change handler
    const typeSelect = tr.querySelector('[data-field="type"]');
    const urlInput = tr.querySelector('[data-field="url"]');
    typeSelect.addEventListener('change', () => {
      const isUrl = typeSelect.value === 'url';
      urlInput.disabled = !isUrl;
      urlInput.required = isUrl;
      if (!isUrl) urlInput.value = '';
    });

    playersTbody.appendChild(tr);
  }

  // Escape HTML to prevent XSS
  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // Remove a player row
  window.removePlayer = function(playerId) {
    const row = document.querySelector(`tr[data-player-id="${playerId}"]`);
    if (row) {
      row.remove();
      renumberRows();
    }
  };

  // Renumber rows after removal
  function renumberRows() {
    const rows = playersTbody.querySelectorAll('tr');
    rows.forEach((row, i) => {
      row.querySelector('.row-num').textContent = i + 1;
    });
  }

  // Handle form submission
  async function handleSubmit(e) {
    e.preventDefault();
    errorMessage.style.display = 'none';

    const tournamentName = document.getElementById('tournament-name').value.trim();
    const rounds = parseInt(document.getElementById('rounds').value);
    const gamesPerPlayer = parseInt(document.getElementById('games-per-player').value);

    // Collect players
    const players = [];
    const rows = playersTbody.querySelectorAll('tr');

    for (const row of rows) {
      const name = row.querySelector('[data-field="name"]').value.trim();
      const type = row.querySelector('[data-field="type"]').value;
      const urlInput = row.querySelector('[data-field="url"]');
      const delayCheckbox = row.querySelector('[data-field="delay"]');

      if (!name) {
        showError('All players must have a name');
        return;
      }

      let url;
      if (type === 'url') {
        url = urlInput.value.trim();
        if (!url) {
          showError('URL players must have a URL specified');
          return;
        }
      } else {
        url = type; // local:alias or built-in alias
      }

      const delay = delayCheckbox ? delayCheckbox.checked : false;
      players.push({ name, url, delay });
    }

    if (players.length < 4) {
      showError('At least 4 players are required');
      return;
    }

    if (!/^[a-z0-9-]+$/.test(tournamentName)) {
      showError('Tournament name must contain only lowercase letters, numbers, and hyphens');
      return;
    }

    const request = { tournamentName, rounds, gamesPerPlayer, players };

    submitBtn.disabled = true;
    submitBtn.textContent = 'Starting...';

    try {
      const response = await fetch('/api/tournaments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
      });

      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.error || 'Failed to start tournament');
      }

      // Redirect to playback page with live mode params
      const playersEncoded = encodeURIComponent(JSON.stringify(result.players));
      window.location.href = `/playback.html?t=${encodeURIComponent(tournamentName)}&id=${result.tournamentId}&players=${playersEncoded}`;

    } catch (error) {
      showError(error.message);
      submitBtn.disabled = false;
      submitBtn.textContent = 'Run Tournament';
    }
  }

  function showError(msg) {
    errorMessage.textContent = msg;
    errorMessage.style.display = 'block';
  }

  init();
})();
