# Publish VeilType Landing

## GitHub Pages

1. Push the repository to GitHub.
2. Publish the `site/` directory.
3. Keep `downloads/VeilType.apk` next to `index.html`.
4. Use the site root URL as the primary Product Hunt link.
5. Replace relative canonical, Open Graph, and sitemap URLs with the real production domain before launch.

## Cloudflare Pages

1. Connect the repository.
2. Set the output directory to `site`.
3. No build command is required.
4. Deploy.
5. Keep the root landing URL as the launch URL, not the direct APK URL.
6. Replace relative canonical, Open Graph, and sitemap URLs with the real production domain before launch.

## Local preview

- Run `start-local.ps1`
- or open `index.html` directly
