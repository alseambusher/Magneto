import sys
import time
from Quartz.CoreGraphics import *
event = sys.argv[1]
def mouseEvent(type, posx, posy):
          theEvent = CGEventCreateMouseEvent(None, type, (posx,posy), kCGMouseButtonLeft)
          CGEventPost(kCGHIDEventTap, theEvent)
def mousemove(posx,posy):
          mouseEvent(kCGEventMouseMoved, posx,posy);
def mouseclick(posx,posy):
          mouseEvent(kCGEventLeftMouseDown, posx,posy);
          mouseEvent(kCGEventLeftMouseUp, posx,posy);
ourEvent = CGEventCreate(None);
currentpos=CGEventGetLocation(ourEvent);

print (currentpos.x+float(sys.argv[2]),currentpos.y+float(sys.argv[3]))
if event is 1:
    mouseclick(currentpos.x,currentpos,y);
else:
    while true:
        try:
            mousemove(currentpos.x+float(sys.argv[2]),currentpos.y+float(sys.argv[3]))
        except:
            print "cant move"
