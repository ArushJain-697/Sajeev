import socket, time, sys, re
def measure(port=4501, seconds=6):
    s = socket.create_connection(("127.0.0.1", port), timeout=15)
    body = b'{"deviceId":"3","deviceSubId":"0","timeout":"20000"}'
    s.sendall(b"STREAM /stream HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\nContent-Length: %d\r\nConnection: close\r\n\r\n" % len(body) + body)
    buf = b""; times = []; t_end = time.time() + seconds; started = False
    while time.time() < t_end:
        try: chunk = s.recv(65536)
        except socket.timeout: break
        if not chunk: break
        buf += chunk
        while True:
            m = re.search(rb"Content-Length:\s*(\d+)\r\n\r\n", buf)
            if not m: break
            n = int(m.group(1)); start = m.end()
            if len(buf) < start + n: break
            times.append(time.time()); buf = buf[start + n:]
    s.close()
    if len(times) < 3: return None, len(times)
    t = times[2:]  # skip start-up frames
    return (len(t) - 1) / (t[-1] - t[0]), len(times)
if __name__ == "__main__":
    fps, n = measure()
    print("frames", n, "measured_fps", None if fps is None else round(fps, 2))
