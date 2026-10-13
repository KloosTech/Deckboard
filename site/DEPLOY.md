# Deploy the Deckboard site

Static files only. Target: `https://deckboard.kloos.tech` (privacy URL: `https://deckboard.kloos.tech/privacy/`).

1. DNS: add an `A` (and `AAAA`) record for `deckboard.kloos.tech` pointing at the VPS.
2. Copy the files:
   ```
   rsync -av --delete site/ user@vps:/var/www/deckboard/
   ```
   (do not upload `DEPLOY.md`, or delete it on the server)
3. nginx server block:
   ```
   server {
       listen 80;
       server_name deckboard.kloos.tech;
       root /var/www/deckboard;
       index index.html;
       location / { try_files $uri $uri/ =404; }
   }
   ```
4. HTTPS: `sudo certbot --nginx -d deckboard.kloos.tech`
5. Check: `curl -I https://deckboard.kloos.tech/privacy/` returns 200, and open the DE page.
