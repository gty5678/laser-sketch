# LaserSketch 项目网页

无构建依赖的静态网站，入口为 `index.html`。资源使用相对路径，兼容 GitHub Pages 的 `/laser-sketch/` 子路径，也可直接打开 HTML。

## 本地预览

在仓库根目录执行：

```sh
python3 -m http.server 8080 --directory webpage
```

访问 http://localhost:8080 。顶部平面图提供「尺寸 / 平面 / 结构」三种演示视图；示意图并非应用截图或真实测量数据。

## GitHub Pages 部署

1. 将 `webpage/` 和 `.github/workflows/deploy-webpage.yml` 提交并推送到 GitHub。
2. 在仓库 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**。
3. 推送到 `main` 后，网页相关变更会自动触发部署；也可在 Actions 中手动运行 **Deploy webpage to GitHub Pages**。
4. 成功后访问 https://gty5678.github.io/laser-sketch/ 。最终地址以部署任务输出为准。

如果仓库默认分支不是 `main`，同步调整工作流的 `on.push.branches`。该工作流仅上传 `webpage`，不会发布 Android/iOS 源文件或本地数据。网站不依赖 CDN、第三方字体或后端服务。
