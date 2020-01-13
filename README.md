# Performance data storage

Stores received user configurations and performance data.

## Note on running in GitLab-CI
Make sure that your GitLab-Runner uses docker executor. 
Also, your /etc/gitlab-runner/config.toml should look like this: 
```toml
concurrent = 1
check_interval = 0

[session_server]
  session_timeout = 1800

[[runners]]
  name = "BP-TDGT-1"
  url = "https://gitlab.hpi.de"
  token = "XXXXXXXXXXX"
  executor = "docker"
  [runners.custom_build_dir]
  [runners.docker]
    tls_verify = false
    image = "ubuntu:18"
    privileged = true
    disable_entrypoint_overwrite = false
    oom_kill_disable = false
    disable_cache = false
    volumes = ["/cache"]
    shm_size = 0
  [runners.cache]
    [runners.cache.s3]
    [runners.cache.gcs]
```