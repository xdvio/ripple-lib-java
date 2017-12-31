#!/usr/bin/env python3
import time
import random
import subprocess

i = 0
while True:
    i += 1
    direction = 'up' if i % 2 == 0 else 'down'
    print('setting connection', direction)
    subprocess.run(['ifconfig', 'en0', direction])
    sleep_for = random.randint(1_00, 60_00) / 100.
    print('sleeping for', sleep_for)
    try:
        time.sleep(sleep_for)
    except KeyboardInterrupt as e:
        print('continuing')
        try:
            time.sleep(0.2)
        except KeyboardInterrupt as e:
            raise e
    
