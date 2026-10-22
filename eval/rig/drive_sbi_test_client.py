import subprocess, re, time, html, sys
ADB = "adb"
def sh(*a):
    return subprocess.run([ADB, "shell", *a], capture_output=True, text=True).stdout
def dump():
    sh("uiautomator", "dump", "/sdcard/d.xml")
    return subprocess.run([ADB, "shell", "cat", "/sdcard/d.xml"], capture_output=True, text=True).stdout
def texts(x):
    return [html.unescape(t) for t in re.findall(r'text="([^"]*)"', x)]
def center(x, label):
    for m in re.finditer(r'<node[^>]*?text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?/?>' % label, x):
        a = list(map(int, m.groups()))
        enabled = 'enabled="true"' in m.group(0)
        return (a[0]+a[2])//2, (a[1]+a[3])//2, enabled
    return None
def wait_for(pred, timeout=40, step=2):
    t0 = time.time()
    while time.time() - t0 < timeout:
        x = dump()
        if pred(x): return x
        time.sleep(step)
    return dump()

def logmark(mark, timeout=60):
    t0=time.time()
    while time.time()-t0<timeout:
        out=subprocess.run([ADB,"logcat","-d"],capture_output=True,text=True).stdout
        if mark in out: return True
        time.sleep(2)
    return False
subprocess.run([ADB,"logcat","-c"])
sh("am","force-stop","nprime.reg.mocksbi.sbinprimetestapp")
sh("am","start","-n","nprime.reg.mocksbi.sbinprimetestapp/.MainActivity")
x=wait_for(lambda x: center(x,"DISCOVER") is not None)
for step,label,mark in [("discover","DISCOVER","MOSIPDISC completed"),("info","INFO","MOSIPDINFO completed"),("rcapture","RCAPTURE","RCAPTURE completed")]:
    c=center(x,label)
    if not c: print(step,"button not found"); break
    if not c[2]: print(step,"button disabled (enabled=false)"); break
    sh("input","tap",str(c[0]),str(c[1]))
    ok=logmark(mark)
    print("==",step,"tapped; log marker",mark,"seen:",ok)
    time.sleep(4)
    x=dump()
    sh("screencap","-p","/sdcard/%s.png"%step)
    subprocess.run([ADB,"pull","/sdcard/%s.png"%step,"build/drive-%s.png"%step],capture_output=True)
