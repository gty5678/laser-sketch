'use strict';
const drawing = document.querySelector('.drawing-card');
const statusText = document.querySelector('#mode-status');
const messages = { dimensions: '尺寸标注已显示', clean: '平面预览 · 已隐藏尺寸', structure: '结构预览 · 已隐藏家具' };
document.querySelectorAll('[data-mode]').forEach(button => {
  button.addEventListener('click', () => {
    drawing.dataset.view = button.dataset.mode;
    statusText.textContent = messages[button.dataset.mode];
    document.querySelectorAll('[data-mode]').forEach(item => {
      const selected = item === button;
      item.classList.toggle('active', selected);
      item.setAttribute('aria-pressed', String(selected));
    });
  });
});
statusText.setAttribute('aria-live', 'polite');
