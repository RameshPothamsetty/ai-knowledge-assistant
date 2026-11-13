# Deployment runbook

## Environments

Staging deploys automatically from the main branch once continuous integration
passes. Production deploys on a signed tag and needs one reviewer.

## Health checks

The application exposes `/actuator/health`. A deployment is considered healthy
when the endpoint returns `UP` for two consecutive probes, thirty seconds apart.

## Rollback

Redeploy the previous image tag. Database migrations are additive, so a rollback
does not need a schema change.

## Incidents

Start an incident channel, page the on-call engineer, and write a short timeline
while the incident is open. A review is due within three working days.
