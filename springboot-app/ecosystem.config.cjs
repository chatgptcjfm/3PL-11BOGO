module.exports = {
  apps: [
    {
      name: 'integration-process-bootstrap',
      script: './scripts/run-local.sh',
      cwd: __dirname,
      interpreter: '/bin/bash',
      watch: false,
      autorestart: true,
      max_restarts: 10,
      env: {
        JAVA_HOME: '/opt/jdk17',
        PATH: '/opt/jdk17/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin'
      }
    }
  ]
}
