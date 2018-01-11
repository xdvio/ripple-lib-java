#!/usr/bin/env python3
import time
import sys
import random
import subprocess

def set_network(direction):
    print('setting connection', direction)
    subprocess.run(['ifconfig', 'en0', direction])

i = 0
while True:
    i += 1
    direction = 'up' if i % 2 == 0 else 'down'
    set_network(direction)

    sleep_for = random.randint(1_00, 60_00) / 100.
    print('sleeping for', sleep_for)

    try:
        fraction = max(0, sleep_for - round(sleep_for))
        sleep_for = round(sleep_for)
        time.sleep(fraction)

        while sleep_for > 0:
            time.sleep(1)
            sleep_for -= 1
            print('remaining seconds', direction, '=', sleep_for)
    except KeyboardInterrupt as e:
        print('continuing')
        try:
            time.sleep(0.2)
        except KeyboardInterrupt as e:
            set_network('up')
            sys.exit(0)


