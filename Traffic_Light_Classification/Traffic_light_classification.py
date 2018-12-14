import numpy as np
import tensorflow as tf
import time
import picamera
import cv2 as cv
import sys
import os
from picamera.array import PiRGBArray
from picamera import PiCamera


FROZEN_GRAPH_PATH = os.path.join('data', 'beye_traffic_light_classification.pb')

# Read the graph.
with tf.gfile.FastGFile(FROZEN_GRAPH_PATH, 'rb') as f:
    graph_def = tf.GraphDef()
    graph_def.ParseFromString(f.read())

with tf.Session() as sess:
    
    # Restore session
    sess.graph.as_default()
    tf.import_graph_def(graph_def, name='')
    
    # Open log file
    f = open("crosswalk_log.txt", 'w')
    
    # Set Rpi camera
    print("session opened!")
    camera = PiCamera()
    camera.resolution = (640, 480)
    camera.framerate = 10
    rawCapture = PiRGBArray(camera)
    time.sleep(0.1)
    
    prevClassId = int(0)
    classId = int(0)
    isValid = False
    
    # Detect and classify traffic light
    for frame in camera.capture_continuous(rawCapture, format="bgr",  use_video_port=True):
        
        prev_time = time.time()  # for calculate fps
        
        rawCapture.truncate(0)
        img = np.asarray(frame.array)
        rows = img.shape[0]
        cols = img.shape[1]
        # inp = cv.resize(img, (480, 360))
        inp = img[:, :, [2, 1, 0]]  # BGR2RGB

        # Run the model
        out = sess.run([sess.graph.get_tensor_by_name('num_detections:0'),
                    sess.graph.get_tensor_by_name('detection_scores:0'),
                    sess.graph.get_tensor_by_name('detection_boxes:0'),
                    sess.graph.get_tensor_by_name('detection_classes:0')],
                    feed_dict={'image_tensor:0': inp.reshape(1, inp.shape[0], inp.shape[1], 3)})

        # Visualize detected bounding boxes.
        num_detections = int(out[0][0])
        print("====================")
        # print(num_detections)
        
        # for i in range(num_detections):
            # classId = int(out[3][0][i])
            # score = float(out[1][0][i])
            # bbox = [float(v) for v in out[2][0][i]]
        classId = int(out[3][0][0])
        score = float(out[1][0][0])
        bbox = [float(v) for v in out[2][0][0]]
        cv.imshow('Traffic Classification', img)
            
        # Draw bounding boxes.
        if score > 0.2:
            x = bbox[1] * cols
            y = bbox[0] * rows
            right = bbox[3] * cols
            bottom = bbox[2] * rows
            cv.rectangle(img, (int(x), int(y)), (int(right), int(bottom)), (125, 255, 51), thickness=2)
                
  
            # classId 1 : Green
            # classId 2 : Red
            if(isValid == True) :
                if (classId == 1) :
                    
                    if(prevClassId == 1) :
                        print("GG")
                        print("Gogo / Green score = %f" % score)
                        sys.exit(1)
                        
                    elif(prevClassId == 2) :
                        print("RG")
                        print("Stop / Green score = %f" % score)
                    
                    prevClassId = classId
                                    
                    
                elif (classId == 2) :
                    if(prevClassId == 1) :
                        print("GR")
                        print("Stop / Red score = %f" % score)
                        
                    elif(prevClassId == 2) :
                        print("RR")
                        print("Stop / Red score = %f" % score)
                    
                    prevClassId = classId
                
                else :
                    print("No light")
                
                    
            
            else :
                if(classId == 2) :
                    print("Stop / Red score = %f" % score)
                    if(prevClassId == 2) :
                        isValid = True
                    
                    prevClassId = 2
                
                elif(classId == 1) :
                    print("Go on next sign / Green score = %f" % score)
                    
                
                else :
                    print("No light")
            
        print("isValid : %r" % isValid)
                
        # for calculate fps
        cur_time = time.time()
        sec = cur_time - prev_time
        fps = 1 / (sec)
        str = 'FPS: %2.3f' % fps
        print(str)
        
        
        if cv.waitKey(1) & 0xFF == ord('q'):
            f.close()
            camera.close()
            cv.destroyAllWindows(0)
            break