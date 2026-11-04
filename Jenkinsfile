@Library('wf-ci') _

wfBuild(
    repo: 'Advanced-Hitbox-Framework',
    jdk: '21',
    branches: ['main'],
    build: ':1.20.1:build :1.21.1:build :26.1:build --no-daemon --stacktrace',
    publish: ':1.20.1:publish :1.21.1:publish :26.1:publish --no-daemon --stacktrace',
    archive: 'versions/*/build/libs/*.jar',
)
