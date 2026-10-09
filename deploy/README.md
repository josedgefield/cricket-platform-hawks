# Deploying to an Oracle Cloud Always Free VM

> **Status:** reviewed draft. These steps have not yet been run against a real VM. Do
> the first deployment together with someone technical, and fix this file as you go.

**Target:** one Arm VM in Oracle's Singapore region running three containers (the app,
Postgres and Caddy for HTTPS), with nightly encrypted backups to Oracle Object Storage.
**Cost:** S$0 on Always Free, plus a domain.

## 1. Create the VM (once)
1. **Create an Oracle Cloud account,** home region **Singapore**. Signing up needs a card for verification.
2. **Create the instance:** Compute → Instances → Create.
   - **Shape:** `VM.Standard.A1.Flex` (Arm), with 2 OCPU and 12 GB RAM. That's well within Always Free.
   - **Image:** Ubuntu 24.04.
   - **SSH:** add your public key.
   - If you see "out of capacity", try again later or pick another availability domain.
3. **Open ports 80 and 443** in two places:
   - the subnet's security list (Ingress, TCP, from `0.0.0.0/0`);
   - the VM's own firewall: `sudo iptables -I INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT && sudo netfilter-persistent save`
4. **Point your domain** (for example `api.<club-domain>`) at the VM's public IP with an A record.

## 2. Install Docker
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # log out and back in
sudo apt-get install -y age     # for encrypted backups
```

## 3. First deployment
```bash
sudo mkdir -p /opt/hawks && sudo chown $USER /opt/hawks && cd /opt/hawks
git clone https://github.com/josedgefield/cricket-platform-hawks.git src
cp src/deploy/{compose.prod.yaml,Caddyfile,backup.sh,.env.example} .
cp .env.example .env && chmod 600 .env && nano .env    # set domain, password, backup key
```

CI doesn't publish an image yet, so build one on the VM. The Dockerfile builds for the VM's own architecture (Arm):
```bash
docker build -t hawks-platform:local src/backend
echo "HAWKS_IMAGE=hawks-platform:local" >> .env
docker compose -f compose.prod.yaml --env-file .env up -d
docker compose -f compose.prod.yaml logs -f app    # wait for "Started HawksApplication"
curl https://<your-domain>/actuator/health         # {"status":"UP"}
```

## 4. Updates
```bash
cd /opt/hawks/src && git pull && cd ..
docker build -t hawks-platform:local src/backend
docker compose -f compose.prod.yaml --env-file .env up -d   # migrations run on start
```
Once a GitHub Actions job publishes images to GHCR, this becomes `docker compose pull && docker compose up -d`, triggered over SSH from CI.

## 5. Backups (set up on day one)
1. **Make a backup key on your own computer, not the server:** `age-keygen -o hawks-backup.key`. Store the file in the club's password manager. Put its **public** key (`age1…`) in `.env` as `BACKUP_AGE_RECIPIENT`.
2. **Create a bucket:** Object Storage → Create bucket `hawks-backups` (private), with a lifecycle rule that deletes objects after 90 days.
3. **Set up the OCI CLI** on the VM (`oci setup config`) with a user that can only write to that bucket.
4. **Schedule the backup:** `crontab -e` → `15 3 * * * cd /opt/hawks && ./backup.sh >> backup.log 2>&1`
5. **Restore drill, every quarter:**
   ```bash
   age -d -i hawks-backup.key hawks-<stamp>.sql.gz.age | gunzip | psql <scratch database>
   ```
   Check that the row counts look right, then write down the date in `docs/09`.

## Security notes
- Only ports 80 and 443 are open; Postgres and the app are not reachable from outside.
- `.env` holds secrets: `chmod 600`, never committed.
- The `prod` profile has **no admin login** until the identity module ships, so admin endpoints return 401.
- Turn on unattended security updates: `sudo apt-get install unattended-upgrades`.
- **Idle reclamation (verify the current policy):** Oracle may stop Always Free instances it considers idle. It looks at very low CPU, network and memory use over 7 days, and a small club app could meet that. The usual fix is to upgrade the account to Pay As You Go: Always Free resources stay free, but set a budget alert of S$1 so any accidental paid resource is noticed. Either way, the backups mean the app can be restored on another host.
