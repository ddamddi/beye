import xml.etree.ElementTree as ET

for x in range(3000):

    xml_data = ET.parse("image_{}.xml".format(x))

    root = xml_data.getroot()

# print(root)
# print(root.tag)
# print(root.attrib)

    folder = root.find("folder")
    file_name = root.find("filename")
    img_path = root.find("path")

    print(file_name.text)
    print(img_path.text)
    file_name.text = "image_{}.jpg".format(x)
    folder.text = "data/dataset/ampelpilot.data"
    img_path.text = str(file_name.text)

    print(file_name.text)
    print(img_path.text)

    xml_data.write("image_{}.xml".format(x))
