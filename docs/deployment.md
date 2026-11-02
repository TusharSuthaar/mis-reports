# Running the app for other people

The app starts safe for **one computer**: the database, the API and the screens listen on `127.0.0.1`, and there are
no default passwords or keys. This page is what to do before colleagues use it from their own computers (audit H-03).
Do every step; none is optional once the screens are on a network.

## Checklist

| # | Step | Why |
|---|---|---|
| 1 | HTTPS in front of the screens; `MIS_COOKIE_SECURE=true` | Passwords and the session cookie otherwise cross the network in clear text |
| 2 | `MIS_UI_BIND=0.0.0.0` (screens only); database and API stay on `127.0.0.1` | Nothing but the screens should be reachable from other computers |
| 3 | A long random `MIS_DB_PASSWORD`; `.env` readable by the administrator only | The database holds everything except PANs in clear |
| 4 | A copy of the two PAN keys kept off this computer | Without them every stored PAN is unreadable, backups included |
| 5 | Daily backup of the database **and** the file store; a restore tried once a month | A backup never restored is a hope, not a backup |
| 6 | One account per person, no shared logins; leavers deactivated the same day | The audit log names who did what |

## 1. HTTPS

The screens' nginx listens on port 80 inside its container. Put a TLS proxy in front of it — the company's own reverse
proxy, or nginx on the same server — with a certificate the office computers trust (the company's certificate authority,
or a public one if the name is public).

The proxy must:

- forward to `http://127.0.0.1:5180` (the screens' port);
- send `X-Forwarded-For` and `X-Forwarded-Proto` (the sign-in limit, R-101, counts failures by the browser's address,
  and the app trusts that header only from proxies on the internal network);
- add `Strict-Transport-Security`, so browsers never fall back to plain HTTP;
- accept uploads up to 21 MB (the app's limit is 20 MB per file).

Example for nginx on the same server (`/etc/nginx/conf.d/mis.conf`):

```nginx
server {
    listen 443 ssl;
    server_name mis.example.internal;
    ssl_certificate     /etc/ssl/mis/fullchain.pem;
    ssl_certificate_key /etc/ssl/mis/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    client_max_body_size 21m;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location / {
        proxy_pass http://127.0.0.1:5180;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}

server {
    listen 80;
    server_name mis.example.internal;
    return 301 https://$host$request_uri;
}
```

With the proxy on the same server, leave `MIS_UI_BIND=127.0.0.1`: only the proxy reaches the screens. Use
`MIS_UI_BIND=0.0.0.0` only when the proxy runs on another machine, and then let the firewall accept port 5180 from that
machine alone.

Then, in `.env`: `MIS_COOKIE_SECURE=true`, and `docker compose up -d`. Check in a browser that the address bar shows the
padlock, that signing in works, and that `http://` sends you to `https://`.

## 2. Network

`docker-compose.yml` publishes only the screens; the database (`MIS_DB_PORT`, default 5442) and the API (8090) are bound
to `127.0.0.1` and must stay there — the screens reach the API inside Docker. Do not change those two bindings to reach
the database from another computer; connect on the server itself (`docker compose exec db psql -U mis mis`).

## 3. Secrets and keys

- `.env` holds the database password, the two PAN keys and the first administrator. On Linux: `chmod 600 .env`, owned
  by the administrator. On Windows: remove every user but the administrator from the file's permissions.
- `MIS_PAN_KEY` and `MIS_PAN_HASH_KEY` encrypt and look up PANs. **Lose them and every PAN in the database and in every
  backup is unreadable.** Keep one copy off this computer (the company password manager, or sealed and locked away).
  Never put them in Git, e-mail or chat.
- The keys never change once data exists: there is no key rotation yet (audit L-05).
- Change the first administrator's password at first sign-in, then remove `MIS_BOOTSTRAP_ADMIN_PASSWORD` from `.env`.

## 4. Backups

Two things hold data: the database (volume `db-data`) and the uploaded files (volume `file-store`).

```bash
# Daily (e.g. cron 02:30): database and uploaded files, dated, kept 30 days
d=$(date +%F)
docker compose exec -T db pg_dump -U mis -Fc mis > /backup/mis-$d.dump
docker run --rm -v mis-reports_file-store:/data:ro -v /backup:/backup alpine tar czf /backup/mis-files-$d.tgz -C /data .
find /backup -name 'mis-*' -mtime +30 -delete
```

(The volume's full name starts with the folder the app runs from: `docker volume ls` shows it.) Copy `/backup` to a
second place every day — another server, or the company's backup share. A backup on the same disk dies with it.

**Restore, once a month, into a scratch database** — never over the live one:

```bash
docker compose exec -T db createdb -U mis mis_restore_test
docker compose exec -T db pg_restore -U mis -d mis_restore_test < /backup/mis-YYYY-MM-DD.dump
docker compose exec -T db psql -U mis -d mis_restore_test -c "SELECT month, status FROM period ORDER BY month DESC LIMIT 3"
docker compose exec -T db dropdb -U mis mis_restore_test
```

The months listed should be the latest ones you closed.

## 5. Accounts

- One account per person (Users screen, ADMIN only), with the role they need: HO and FINANCE work the months; RM and RD
  can sign in but have no screens until the RM / RD view exists.
- Ten wrong passwords lock an account (R-101): the user sees "ask an administrator"; the ADMIN checks with them, then
  presses **Unlock** on the Users screen. Several locks at once, or failures from an address no one recognises, are
  worth asking about — every attempt is in the audit log (Users & audit).
- Deactivate a leaver's account the day they leave; never delete it (their name stays on what they did).

## 6. Updating

```bash
# 1. Back up first (section 4).  2. Then:
git pull
docker compose up -d --build
```

Database migrations run by themselves when the API starts. If the API does not come back
(`docker compose logs api | tail -50`), restore last night's backup and report the reference shown on the screen.
