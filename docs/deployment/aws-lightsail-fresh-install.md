# AWS Lightsail Fresh Installation

This guide deploys Church Operations to a new AWS Lightsail instance using
Ubuntu 24.04, Docker Compose, MongoDB 8.0.28, Nginx, and HTTPS.

Use a 4 GB RAM instance when possible. A 2 GB RAM instance can run the
application with swap, but image builds and startup will be slower.

## 1. Create The Lightsail Instance

Create an Ubuntu 24.04 instance in the Lightsail console, then:

1. Attach a static IPv4 address. Dynamic addresses change after stop/start.
2. Configure both IPv4 and IPv6 firewalls:
   - TCP `22`: restrict to your own IP address when possible.
   - TCP `80`: allow all addresses.
   - TCP `443`: allow all addresses.
   - Do not open `27017`, `5173`, or `8080`.
3. Enable automatic snapshots after deployment.

References:

- [Create and attach a static IP](https://docs.aws.amazon.com/lightsail/latest/userguide/lightsail-create-static-ip.html)
- [Lightsail firewall configuration](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-firewall-and-port-mappings-in-amazon-lightsail.html)

Connect using the Lightsail browser SSH terminal and run the remaining commands
as the `ubuntu` user.

## 2. Install Required Software

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y ca-certificates curl git nginx snapd

KERNEL_VERSION=$(uname -r | cut -d- -f1)
if dpkg --compare-versions "$KERNEL_VERSION" ge 6.19 && \
   dpkg --compare-versions "$KERNEL_VERSION" lt 7.0.14; then
  echo "Linux $KERNEL_VERSION is incompatible with MongoDB."
  echo "Complete the kernel recovery section before starting MongoDB."
fi

sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
newgrp docker

docker --version
docker compose version
```

These commands follow Docker's
[official Ubuntu installation instructions](https://docs.docker.com/engine/install/ubuntu/).

### MongoDB Kernel Compatibility

MongoDB cannot run on Linux kernels `6.19` through `7.0.13` because of a known
TCMalloc incompatibility. Ubuntu 24.04 provides the supported AWS `6.17` kernel.
Do not work around this by downgrading MongoDB to an old patch release.

Check the active and installed kernels:

```bash
uname -r
dpkg -l 'linux-image*' | awk '$1 == "ii" {print $2, $3}'
```

If the active kernel is in the incompatible range, install the AWS `6.17`
kernel without removing the current kernel:

```bash
sudo apt update
sudo apt install -y linux-aws-6.17

sudo grep -E "submenu |menuentry 'Ubuntu, with Linux 6.17" \
  /boot/grub/grub.cfg
```

Copy the exact `6.17` menu-entry text from the output. Configure GRUB to retain
the selected kernel, replacing the example entry if its version differs:

```bash
sudo sed -i 's/^GRUB_DEFAULT=.*/GRUB_DEFAULT=saved/' /etc/default/grub
sudo update-grub
sudo grub-set-default \
  'Advanced options for Ubuntu>Ubuntu, with Linux 6.17.0-1019-aws'
sudo grub-editenv list
sudo reboot
```

Reconnect and verify the kernel before starting the application:

```bash
uname -r
cd /opt/church-operation-app
docker compose up -d
docker compose ps
docker compose logs --tail=50 mongo
```

The active kernel should start with `6.17`, and MongoDB should remain running.
Retain at least one other bootable kernel until the reboot has been verified.

References:

- [MongoDB production notes](https://www.mongodb.com/docs/manual/administration/production-notes/)
- [Ubuntu AWS 6.17 kernel package](https://packages.ubuntu.com/en/linux-aws-6.17)

## 3. Configure Docker Log Rotation And Swap

```bash
sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5"
  }
}
JSON

sudo systemctl restart docker

if [ -z "$(swapon --show --noheadings)" ]; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

free -h
```

## 4. Download The Application

```bash
sudo mkdir -p /opt/church-operation-app
sudo chown "$USER":"$USER" /opt/church-operation-app

git clone --branch develop \
  https://github.com/javarako/church-operation-app.git \
  /opt/church-operation-app

cd /opt/church-operation-app
git log -1 --oneline
```

## 5. Protect Internal Service Ports

Create an AWS-specific Compose override so MongoDB, the backend, and Vite are
reachable only from the instance itself.

```bash
cat > docker-compose.aws.yml <<'YAML'
services:
  mongo:
    ports: !override
      - "127.0.0.1:27017:27017"
  backend:
    ports: !override
      - "127.0.0.1:8080:8080"
  frontend:
    ports: !override
      - "127.0.0.1:5173:5173"
YAML

cp .env.example .env
echo 'COMPOSE_FILE=docker-compose.yml:docker-compose.aws.yml' >> .env

KEY=$(openssl rand -base64 32)
sed -i "s|^CHURCH_SETTINGS_ENCRYPTION_KEY=.*|CHURCH_SETTINGS_ENCRYPTION_KEY=$KEY|" .env
chmod 600 .env
nano .env
```

Configure these important values in `.env`:

```env
CHURCH_TIME_ZONE=America/Toronto
CHURCH_APP_HOSTNAME=
PASSWORD_RESET_FRONTEND_BASE_URL=https://YOUR_STATIC_IP
PASSWORD_RESET_FROM_ADDRESS=YOUR_VERIFIED_SENDER
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_BREVO_USERNAME
MAIL_PASSWORD=YOUR_BREVO_SMTP_KEY
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MONGO_IMAGE=
```

For an IP-only deployment, leave `CHURCH_APP_HOSTNAME` empty; Vite permits IP
hosts by default. For a DNS deployment, set it to the exact public hostname
and use the same hostname in `PASSWORD_RESET_FRONTEND_BASE_URL`, the Nginx
`server_name`, and the HTTPS certificate. For example:

```env
CHURCH_APP_HOSTNAME=church.example.org
PASSWORD_RESET_FRONTEND_BASE_URL=https://church.example.org
```

Also update the church name, address, contact information, treasurer, charity
registration number, receipt location, and website.

Keep `CHURCH_SETTINGS_ENCRYPTION_KEY` unchanged and store a secure offline copy.
The same key is required to decrypt email settings after restoring a backup.

Verify the effective Compose configuration:

```bash
docker compose config --quiet
docker compose config | grep -A3 'published:'
```

Each published address must show `127.0.0.1`.

## 6. Start The Application

```bash
docker compose up --build -d
docker compose ps

# Confirm that every service will restart after a server reboot.
docker compose ps -q | xargs docker inspect \
  --format '{{.Name}}: {{.HostConfig.RestartPolicy.Name}}'

docker compose exec mongo mongod --version
docker compose exec mongo mongosh --quiet --eval \
  'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'

curl http://127.0.0.1:8080/api/church-information
curl --head http://127.0.0.1:5173
```

A fresh installation should report MongoDB `8.0.28` and FCV `8.0`.
Every container should report the `unless-stopped` restart policy.

### Apply The Restart Policy To An Existing Installation

If the application was installed before the restart policy was added, update it
once from the application directory:

```bash
cd /opt/church-operation-app
git pull --ff-only origin develop
docker compose up -d
docker compose ps
```

`docker compose up -d` recreates containers whose configuration changed and
preserves the named MongoDB data volume.

Test the restart behavior:

```bash
sudo reboot
```

Reconnect after the instance starts, then check:

```bash
sudo systemctl is-active docker
cd /opt/church-operation-app
docker compose ps -a
docker compose logs --tail=100 mongo
```

Docker should be `active`, and all three application services should be
`running`. If MongoDB is `exited`, its log output identifies a separate startup
failure that must be resolved before restarting the backend.

## 7. Configure HTTP Nginx

Replace both placeholder values before continuing:

```bash
PUBLIC_IP="YOUR_STATIC_IP"
ADMIN_EMAIL="YOUR_EMAIL_ADDRESS"
```

Configure the HTTP endpoint and ACME challenge directory:

```bash
sudo mkdir -p /var/www/certbot/.well-known/acme-challenge

sudo tee /etc/nginx/sites-available/church-operation >/dev/null <<'NGINX'
server {
    listen 80;
    listen [::]:80;
    server_name __PUBLIC_IP__;
    client_max_body_size 2g;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        proxy_pass http://127.0.0.1:5173;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINX

sudo sed -i "s/__PUBLIC_IP__/$PUBLIC_IP/g" \
  /etc/nginx/sites-available/church-operation
sudo ln -sf /etc/nginx/sites-available/church-operation \
  /etc/nginx/sites-enabled/church-operation
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

## 8. Request The HTTPS IP Certificate

Let's Encrypt IP certificates are short-lived and require Certbot 5.4 or later.

Reference: [Six-Day and IP Address Certificates Available in Certbot](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)

Install Certbot and test against the staging certificate authority:

```bash
sudo snap install certbot --classic

sudo certbot certonly --staging \
  --preferred-profile shortlived \
  --webroot --webroot-path /var/www/certbot \
  --ip-address "$PUBLIC_IP" \
  --cert-name "$PUBLIC_IP-staging" \
  --email "$ADMIN_EMAIL" --agree-tos --non-interactive

sudo certbot delete --cert-name "$PUBLIC_IP-staging" --non-interactive
```

Request the publicly trusted certificate:

```bash
sudo certbot certonly \
  --preferred-profile shortlived \
  --webroot --webroot-path /var/www/certbot \
  --ip-address "$PUBLIC_IP" \
  --cert-name "$PUBLIC_IP" \
  --email "$ADMIN_EMAIL" --agree-tos --non-interactive
```

## 9. Enable HTTPS In Nginx

```bash
sudo tee /etc/nginx/sites-available/church-operation >/dev/null <<'NGINX'
server {
    listen 80;
    listen [::]:80;
    server_name __PUBLIC_IP__;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name __PUBLIC_IP__;
    client_max_body_size 2g;

    ssl_certificate /etc/letsencrypt/live/__PUBLIC_IP__/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/__PUBLIC_IP__/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:5173;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 3600;
    }
}
NGINX

sudo sed -i "s/__PUBLIC_IP__/$PUBLIC_IP/g" \
  /etc/nginx/sites-available/church-operation
sudo nginx -t
sudo systemctl reload nginx
```

Create a renewal deployment hook so Nginx loads each renewed short-lived
certificate:

```bash
sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx >/dev/null <<'SH'
#!/bin/sh
systemctl reload nginx
SH

sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
sudo certbot renew --dry-run
sudo certbot certificates
systemctl list-timers --all | grep certbot
```

## 10. Complete Initial Application Setup

1. Open `https://YOUR_STATIC_IP`.
2. Sign in with username `admin` and password `password`.
3. Change the bootstrap password immediately.
4. Create an email-based member with the `ADMIN` role.
5. Configure Church Settings and Email Settings as needed.
6. Send a test email.
7. Download and verify a Full Backup from System Administration.
8. Enable a daily Lightsail snapshot.

## 11. Routine Service Checks

```bash
cd /opt/church-operation-app
docker compose ps
docker compose logs --since=10m --no-color
docker compose exec mongo mongod --version
docker compose exec mongo mongosh --quiet --eval \
  'db.adminCommand({getParameter:1,featureCompatibilityVersion:1})'
curl --head "https://$PUBLIC_IP"
sudo certbot certificates
systemctl list-timers --all | grep certbot
```

Docker logs are automatically limited to five files of 10 MB per container by
the configuration in this guide.

## 12. Create An Infrastructure Backup

Application Full Backup remains the preferred portable backup. A compressed
MongoDB dump can be created as an additional infrastructure backup:

```bash
mkdir -p "$HOME/church-backups"
chmod 700 "$HOME/church-backups"

BACKUP_FILE="$HOME/church-backups/church-$(date +%Y%m%d-%H%M%S).archive"
docker compose exec -T mongo \
  mongodump --db church_operations --archive --gzip > "$BACKUP_FILE"

shasum -a 256 "$BACKUP_FILE" > "$BACKUP_FILE.sha256"
ls -lh "$BACKUP_FILE" "$BACKUP_FILE.sha256"
```

Retain the backup outside the Lightsail instance as well. Do not consider a
backup complete until a restore has been tested in a separate environment.

## 13. Update The Application

Create both application and infrastructure backups before each update.

```bash
cd /opt/church-operation-app
git checkout develop
git pull --ff-only origin develop
docker compose up --build -d
docker compose ps
docker compose logs --since=10m --no-color
```

## 14. Troubleshooting

### Vite Blocks A Custom DNS Hostname

If the browser reports that the host is not allowed, set the exact hostname in
the server's uncommitted `.env` file:

```env
CHURCH_APP_HOSTNAME=church.example.org
PASSWORD_RESET_FRONTEND_BASE_URL=https://church.example.org
```

Recreate the affected services and verify the value received by Vite:

```bash
cd /opt/church-operation-app
docker compose up -d --force-recreate frontend backend
docker compose exec frontend printenv __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS
CHURCH_HOST_TO_TEST=church.example.org
curl --head --header "Host: $CHURCH_HOST_TO_TEST" http://127.0.0.1:5173
```

The printed value must exactly match the browser hostname, and the local
request must return a successful HTTP status. Do not use `allowedHosts: true`
or allow a shared parent domain such as `.mooo.com`.

### MongoDB Stops After A Reboot

Check the active kernel and MongoDB log:

```bash
uname -r
cd /opt/church-operation-app
docker compose ps -a
docker compose logs --tail=100 mongo
```

If the log reports `Linux kernel versions 6.19 and newer`, follow the kernel
compatibility procedure in section 2. MongoDB is incompatible with kernels
`6.19` through `7.0.13`; Ubuntu kernel `7.0.0` is therefore also affected.

If the kernel is compatible but the containers remain stopped, verify Docker
and the restart policies:

```bash
sudo systemctl is-active docker
docker compose ps -q | xargs docker inspect \
  --format '{{.Name}}: {{.HostConfig.RestartPolicy.Name}}'
```

Docker should be `active`, and each container should report `unless-stopped`.

### Backend Cannot Resolve `mongo`

An error containing `UnknownHostException: mongo` means the backend cannot
resolve the MongoDB service on the Compose network. Inspect both services:

```bash
cd /opt/church-operation-app
docker compose ps -a
docker compose logs --tail=80 mongo
docker compose logs --tail=80 backend
```

After MongoDB is healthy, recreate the application network without deleting
the named data volume:

```bash
docker compose down
docker compose up -d mongo
sleep 10
docker compose up -d backend frontend
docker compose exec backend getent hosts mongo
```

Never add `-v` to the `docker compose down` command.

### HTTP Works But HTTPS Is Refused

If HTTP returns `200 OK` while port 443 refuses the connection, check whether
the certificate exists and whether Nginx has an HTTPS listener:

```bash
sudo certbot certificates
sudo nginx -T 2>/dev/null | \
  grep -nE 'server_name|listen 443|ssl_certificate'
sudo ss -ltnp | grep -E ':80|:443'
```

If the certificate exists but `listen 443` is absent, repeat section 9 to
enable the HTTPS server block, then validate it:

```bash
sudo nginx -t
sudo systemctl reload nginx
curl -kI https://127.0.0.1
```

If the local HTTPS test succeeds but the public address does not, confirm that
TCP port `443` is allowed in both the Lightsail IPv4 and IPv6 firewalls.

## Important Safety Warning

Never run this command on a deployment whose data must be retained:

```text
docker compose down -v
```

The `-v` option deletes the MongoDB and application volumes.
