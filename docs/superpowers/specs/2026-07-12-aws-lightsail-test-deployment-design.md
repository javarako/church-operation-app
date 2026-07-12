# AWS Lightsail Test Deployment Design

## Purpose

Deploy the existing Church Operations application to one AWS Lightsail instance so the church can evaluate the current UI and workflows remotely. This first deployment uses the existing development containers and is strictly for testing with sample records.

## Safety Boundary

- Do not enter real member, offering, financial, credential, or evidence-file data.
- Access is plain HTTP by static IP and does not provide transport encryption.
- A domain and HTTPS are required before production use.
- Automatic Lightsail snapshots remain disabled during this test stage to avoid optional charges.

## AWS Infrastructure

- Service: Amazon Lightsail
- Region: Canada (Central), Montreal (`ca-central-1`)
- Instance: `church-operation`
- Image: Ubuntu 24.04 LTS
- Plan: General purpose, dual-stack, 4 GB RAM, 2 vCPUs, 80 GB SSD
- Published price: USD $24 per month after any applicable introductory offer
- Static IP resource: `church-operation-static-ip`
- Static IPv4 address: `16.54.60.43`

## Application Topology

Docker Compose runs the existing three services on the instance:

- `frontend`: Vue 3 Vite development server, accessed through port `5173`
- `backend`: Spring Boot 4 on port `8080`; frontend proxy routes API, branding, and health requests to it
- `mongo`: MongoDB 6 with the named `mongo-data` volume

The Lightsail firewall permits SSH and frontend test traffic. It does not permit public access to MongoDB port `27017` or backend port `8080`. The Vite proxy reaches the backend through the private Docker network.

## Deployment Source And Secrets

The server clones `https://github.com/javarako/church-operation-app.git`. Runtime configuration is supplied through a server-local `.env` file that is never committed. Gmail SMTP credentials and application configuration remain outside Git and outside deployment documentation.

## Deployment Flow

1. Connect to the instance as Ubuntu using the Lightsail browser SSH client or the downloaded regional SSH key.
2. Install Docker Engine, the Docker Compose plugin, Git, and operating-system updates.
3. Clone the repository into `/opt/church-operation-app` and assign it to the `ubuntu` user.
4. Create the server-local `.env` with church information, password-reset URL, sender address, and SMTP secrets.
5. Build and start the existing Compose services.
6. Open only the required Lightsail test port and verify the login page from an external browser.
7. Verify containers, backend health, authentication, branding assets, and persistent MongoDB storage.

## Error Handling And Recovery

- Failed builds are inspected with Compose build output and service logs.
- A failed service is restarted through Docker Compose after its configuration is corrected.
- The MongoDB volume is preserved when application containers are recreated.
- The instance is not deleted as part of troubleshooting.
- No real-data migration is performed during this test deployment.

## Verification

- All three containers are running.
- Backend health responds successfully from inside the Docker network.
- `http://16.54.60.43:5173` loads the login page.
- The default administrator can sign in and is required to change the initial password.
- Dashboard branding and sample records load.
- MongoDB and backend ports are unreachable from the public internet.

## Production Follow-Up

Before real church use, replace the Vite development server with a production frontend image, serve through Nginx, attach a church-controlled domain, enable HTTPS, restrict SSH, establish encrypted backups, document restore testing, and complete a security review.
