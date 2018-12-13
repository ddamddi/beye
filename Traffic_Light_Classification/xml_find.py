import xml.etree.ElementTree as ET

for x in range(3000):

    xml_data = ET.parse("image_{}.xml".format(x))

    root = xml_data.getroot()

    detect_object = root.find("object")
    object_name = detect_object.find("name")
    if object_name.text != "green" & object_name.text != "red" :
        print(x)

# print(root)
# print(root.tag)
# print(root.attrib)
#    folder = root.find("folder")
  #  file_name = root.find("filename")
   # img_path = root.find("path")
# print(img_path)
    #if file_name.text == "image_3651.jpg" :
      #  print(x)

    # print(file_name.text)
    # print(img_path.text)
    # folder.text = "ampelpilot.data"
    # img_path.text = str(file_name.text)

    # print(file_name.text)
    # print(img_path.text)

    # xml_data.write("image_{}.xml".format(x))
