/**
 * Bar chart rendering and transitions for the tournament leaderboard.
 */
const BarChart = (function() {
  const PLAYER_COLORS = [
    '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
    '#1abc9c', '#e67e22', '#e84393', '#00b894', '#6c5ce7',
    '#fd79a8', '#00cec9', '#fab1a0', '#a29bfe', '#ffeaa7',
    '#dfe6e9', '#55efc4', '#74b9ff', '#ff7675', '#636e72'
  ];

  const ROW_HEIGHT = 40;
  let chartArea = null;
  let playerMap = {};  // id -> { el, color, index }
  let initialized = false;

  /**
   * Initialize the chart with player data.
   * @param {HTMLElement} container - the chart-area element
   * @param {Array} players - [{id, name}, ...]
   * @param {number} initialRating - initial ELO rating
   */
  function init(container, players, initialRating) {
    chartArea = container;
    chartArea.innerHTML = '';
    playerMap = {};

    // Sort players alphabetically by name for stable row positions
    const sorted = players.slice().sort(function(a, b) {
      return a.name.localeCompare(b.name);
    });

    chartArea.style.height = (sorted.length * ROW_HEIGHT) + 'px';

    sorted.forEach(function(p, i) {
      const row = document.createElement('div');
      row.className = 'bar-row';
      row.style.top = (i * ROW_HEIGHT) + 'px';

      const rank = document.createElement('span');
      rank.className = 'bar-rank';
      rank.textContent = '-';

      const bar = document.createElement('div');
      bar.className = 'bar';
      const color = PLAYER_COLORS[i % PLAYER_COLORS.length];
      bar.style.backgroundColor = color;
      bar.style.width = '0px';

      const label = document.createElement('span');
      label.className = 'bar-label';
      label.textContent = p.name;

      const rating = document.createElement('span');
      rating.className = 'bar-rating';
      rating.textContent = initialRating.toFixed(0);

      bar.appendChild(label);
      bar.appendChild(rating);
      row.appendChild(rank);
      row.appendChild(bar);
      chartArea.appendChild(row);

      playerMap[p.id] = { el: row, bar: bar, rankEl: rank, ratingEl: rating, color: color, index: i };
    });

    initialized = true;
  }

  /**
   * Update bars with new ratings. Reorders and animates.
   * @param {Object} ratings - {playerId: ratingValue, ...}
   * @param {Array|null} changedIds - player IDs that changed in this event
   */
  function update(ratings, changedIds) {
    if (!initialized) return;

    // Scale from 0 to max value
    const values = Object.values(ratings);
    const maxValue = Math.max(...values) || 1;
    const chartWidth = chartArea.clientWidth - 40; // minus rank width

    // Build rank order (by value descending)
    const ranked = Object.keys(ratings).sort(function(a, b) {
      return ratings[b] - ratings[a];
    });
    const rankOf = {};
    ranked.forEach(function(id, i) { rankOf[id] = i + 1; });

    // Update each player (rows stay in fixed alphabetical position)
    Object.keys(playerMap).forEach(function(id) {
      const p = playerMap[id];
      const r = ratings[id];
      if (r === undefined) return;

      const pct = r / maxValue;
      const barWidth = Math.max(60, pct * (chartWidth - 60) + 60);

      p.bar.style.width = barWidth + 'px';
      p.ratingEl.textContent = r.toFixed(0);
      p.rankEl.textContent = '#' + rankOf[id];

      // Glow effect for changed bars
      if (changedIds && changedIds.includes(id)) {
        p.bar.classList.add('glow');
        setTimeout(function() { p.bar.classList.remove('glow'); }, 400);
      }
    });
  }

  return { init: init, update: update };
})();
