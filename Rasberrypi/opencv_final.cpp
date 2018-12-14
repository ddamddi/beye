#include <string>
#include <iostream>
#include <raspicam/raspicam_cv.h>
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/video/video.hpp>
#include <opencv2/core/core.hpp>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/sdp.h>
#include <bluetooth/sdp_lib.h>
#include <bluetooth/rfcomm.h>
#define resizeSize 2


//bluetooth code

bdaddr_t bdaddr_any = { 0, 0, 0, 0, 0, 0 };
bdaddr_t bdaddr_local = { 0, 0, 0, 0xff, 0xff, 0xff };

int _str2uuid(const char *uuid_str, uuid_t *uuid) {
	// This is from the pybluez stack 

	uint32_t uuid_int[4];
	char *endptr;

	if (strlen(uuid_str) == 36) {
		char buf[9] = { 0 };

		if (uuid_str[8] != '-' && uuid_str[13] != '-' &&
			uuid_str[18] != '-' && uuid_str[23] != '-') {
			return 0;
		}
		// first 8-bytes
		strncpy(buf, uuid_str, 8);
		uuid_int[0] = htonl(strtoul(buf, &endptr, 16));
		if (endptr != buf + 8) return 0;
		// second 8-bytes
		strncpy(buf, uuid_str + 9, 4);
		strncpy(buf + 4, uuid_str + 14, 4);
		uuid_int[1] = htonl(strtoul(buf, &endptr, 16));
		if (endptr != buf + 8) return 0;

		// third 8-bytes
		strncpy(buf, uuid_str + 19, 4);
		strncpy(buf + 4, uuid_str + 24, 4);
		uuid_int[2] = htonl(strtoul(buf, &endptr, 16));
		if (endptr != buf + 8) return 0;

		// fourth 8-bytes
		strncpy(buf, uuid_str + 28, 8);
		uuid_int[3] = htonl(strtoul(buf, &endptr, 16));
		if (endptr != buf + 8) return 0;

		if (uuid != NULL) sdp_uuid128_create(uuid, uuid_int);
	}
	else if (strlen(uuid_str) == 8) {
		// 32-bit reserved UUID
		uint32_t i = strtoul(uuid_str, &endptr, 16);
		if (endptr != uuid_str + 8) return 0;
		if (uuid != NULL) sdp_uuid32_create(uuid, i);
	}
	else if (strlen(uuid_str) == 4) {
		// 16-bit reserved UUID
		int i = strtol(uuid_str, &endptr, 16);
		if (endptr != uuid_str + 4) return 0;
		if (uuid != NULL) sdp_uuid16_create(uuid, i);
	}
	else {
		return 0;
	}

	return 1;

}



sdp_session_t *register_service(uint8_t rfcomm_channel) {

	/* A 128-bit number used to identify this service. The words are ordered from most to least
	* significant, but within each word, the octets are ordered from least to most significant.
	* For example, the UUID represneted by this array is 00001101-0000-1000-8000-00805F9B34FB. (The
	* hyphenation is a convention specified by the Service Discovery Protocol of the Bluetooth Core
	* Specification, but is not particularly important for this program.)
	*
	* This UUID is the Bluetooth Base UUID and is commonly used for simple Bluetooth applications.
	* Regardless of the UUID used, it must match the one that the Armatus Android app is searching
	* for.
	*/

	const char *service_name = "Armatus Bluetooth server";
	const char *svc_dsc = "A HERMIT server that interfaces with the Armatus Android app";
	const char *service_prov = "Armatus";

	uuid_t root_uuid, l2cap_uuid, rfcomm_uuid, svc_uuid,
		svc_class_uuid;
	sdp_list_t *l2cap_list = 0,
		*rfcomm_list = 0,
		*root_list = 0,
		*proto_list = 0,
		*access_proto_list = 0,
		*svc_class_list = 0,
		*profile_list = 0;
	sdp_data_t *channel = 0;
	sdp_profile_desc_t profile;
	sdp_record_t record = { 0 };
	sdp_session_t *session = 0;

	// set the general service ID
	//sdp_uuid128_create(&svc_uuid, &svc_uuid_int);
	_str2uuid("00001101-0000-1000-8000-00805F9B34FB", &svc_uuid);
	sdp_set_service_id(&record, svc_uuid);

	char str[256] = "";
	sdp_uuid2strn(&svc_uuid, str, 256);
	printf("Registering UUID %s\n", str);

	// set the service class
	sdp_uuid16_create(&svc_class_uuid, SERIAL_PORT_SVCLASS_ID);
	svc_class_list = sdp_list_append(0, &svc_class_uuid);
	sdp_set_service_classes(&record, svc_class_list);

	// set the Bluetooth profile information
	sdp_uuid16_create(&profile.uuid, SERIAL_PORT_PROFILE_ID);
	profile.version = 0x0100;
	profile_list = sdp_list_append(0, &profile);
	sdp_set_profile_descs(&record, profile_list);

	// make the service record publicly browsable
	sdp_uuid16_create(&root_uuid, PUBLIC_BROWSE_GROUP);
	root_list = sdp_list_append(0, &root_uuid);
	sdp_set_browse_groups(&record, root_list);

	// set l2cap information
	sdp_uuid16_create(&l2cap_uuid, L2CAP_UUID);
	l2cap_list = sdp_list_append(0, &l2cap_uuid);
	proto_list = sdp_list_append(0, l2cap_list);

	// register the RFCOMM channel for RFCOMM sockets
	sdp_uuid16_create(&rfcomm_uuid, RFCOMM_UUID);
	channel = sdp_data_alloc(SDP_UINT8, &rfcomm_channel);
	rfcomm_list = sdp_list_append(0, &rfcomm_uuid);
	sdp_list_append(rfcomm_list, channel);
	sdp_list_append(proto_list, rfcomm_list);

	access_proto_list = sdp_list_append(0, proto_list);
	sdp_set_access_protos(&record, access_proto_list);

	// set the name, provider, and description
	sdp_set_info_attr(&record, service_name, service_prov, svc_dsc);

	// connect to the local SDP server, register the service record,
	// and disconnect
	session = sdp_connect(&bdaddr_any, &bdaddr_local, SDP_RETRY_IF_BUSY);
	sdp_record_register(session, &record, 0);

	// cleanup
	sdp_data_free(channel);
	sdp_list_free(l2cap_list, 0);
	sdp_list_free(rfcomm_list, 0);
	sdp_list_free(root_list, 0);
	sdp_list_free(access_proto_list, 0);
	sdp_list_free(svc_class_list, 0);
	sdp_list_free(profile_list, 0);

	return session;
}


int init_server() {
	int port = 3, result, sock, client, bytes_read, bytes_sent;
	struct sockaddr_rc loc_addr = { 0 }, rem_addr = { 0 };
	char buffer[1024] = { 0 };
	socklen_t opt = sizeof(rem_addr);

	// local bluetooth adapter
	loc_addr.rc_family = AF_BLUETOOTH;
	loc_addr.rc_bdaddr = bdaddr_any;
	loc_addr.rc_channel = (uint8_t)port;

	// register service
	sdp_session_t *session = register_service(port);
	// allocate socket
	sock = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
	printf("socket() returned %d\n", sock);

	// bind socket to port 3 of the first available
	result = bind(sock, (struct sockaddr *)&loc_addr, sizeof(loc_addr));
	printf("bind() on channel %d returned %d\n", port, result);

	// put socket into listening mode
	result = listen(sock, 1);
	printf("listen() returned %d\n", result);

	//sdpRegisterL2cap(port);

	// accept one connection
	printf("calling accept()\n");
	client = accept(sock, (struct sockaddr *)&rem_addr, &opt);
	printf("accept() returned %d\n", client);

	ba2str(&rem_addr.rc_bdaddr, buffer);
	fprintf(stderr, "accepted connection from %s\n", buffer);
	memset(buffer, 0, sizeof(buffer));

	return client;
}

char input[1024] = { 0 };
char *read_server(int client) {
	// read data from the client
	int bytes_read;
	bytes_read = read(client, input, sizeof(input));
	if (bytes_read > 0) {
		printf("received [%s]\n", input);
		return input;
	}
	else {
		printf("received [z]\n" );
		return "z";
	}
}

void write_server(int client, char *message) {
	// send data to the client
	char messageArr[1024] = { 0 };
	int bytes_sent;
	strcpy(messageArr, message);

	bytes_sent = write(client, messageArr, strlen(messageArr));
	if (bytes_sent > 0) {
		printf("sent [%s] %d\n", messageArr, bytes_sent);
	}
}

// opencv code
using namespace cv;
using namespace std;
int movcount = 0;
int noisehandle = 0;// to handle kind of noise
int errorhandle = 0;
int stairhandle = 0;
int downhandle = 0;
Mat savemov;
int test_flag =0;
int obf =0;
int upf =0;
int dof =0;
int crs =0;
bool saveset = false; // to handle conflict
bool downbit = false;
bool stairbit = false;
int stairbitcheck = 0;
int downstairbit = 0;

class WatershedSegmenter {
private:
	cv::Mat markers;
public:
	void setMarkers(cv::Mat& markerImage)
	{
		markerImage.convertTo(markers, CV_32S);
	}

	cv::Mat process(cv::Mat &image)
	{
		cv::watershed(image, markers);
		markers.convertTo(markers, CV_8U);
		return markers;
	}
};

int image_process(int client) {

	//global variables
	Mat origin;		// 원본
	Mat resizeImage;	// 사이즈 조정
	Mat MOG2Image; //fg mask fg mask generated by MOG2 method
	Mat grayImage;
	Mat contrastImage;
	Mat blurImage;
	Mat mophologyImage;
	Mat thresholdImage;
	Mat ContourImg;

	Ptr< BackgroundSubtractor> pMOG2; //MOG2 Background subtractor

	
	pMOG2 = createBackgroundSubtractorMOG2(500, 16, true);

	//char fileName[100] = "output4.mp4";
	//VideoCapture stream1(fileName);
	raspicam::RaspiCam_Cv Camera;

	Camera.set(CV_CAP_PROP_FORMAT, CV_8UC3);
	Camera.set(CV_CAP_PROP_FRAME_WIDTH, 640);
	Camera.set(CV_CAP_PROP_FRAME_HEIGHT, 480);
	Mat element = getStructuringElement(MORPH_RECT, Size(7, 7), Point(3, 3));
	Mat element2 = getStructuringElement(MORPH_ELLIPSE, Size(3, 3));
	Mat element3 = getStructuringElement(MORPH_RECT, Size(1, 1));

	if (!Camera.open()) {
		cerr << "error opening the camera" << endl;
		return -1;
	}
	//unconditional loop   
	while (true) {
		char *recv_message = read_server(client); 
		//if (!(stream1.read(origin))) //get one frame form video   
		//break;
		if(recv_message[0] == 'c'){
			test_flag = 1;
		}
		// 이미지 size 조절
		Camera.grab();
		Camera.retrieve(origin);
		resize(origin, resizeImage, Size(origin.size().width / resizeSize, origin.size().height / resizeSize));
	 
		/////////////////////////////////////////////////////////////////
		//1단계 : 밝기 대조 조정
		contrastImage = resizeImage.clone();

		Mat sharpening;
		GaussianBlur(contrastImage, sharpening, Size(0, 0), 3);
		cv::addWeighted(contrastImage, 1.5, sharpening, -0.5, 0, sharpening);
		imshow("1. sharpening", sharpening);
		Mat gray;
		cvtColor(sharpening, gray, CV_RGB2GRAY);
		//imshow("2. grayscale", gray);
		Mat binary;
		Mat binaryroad;
		threshold(gray, binary, 55, 255, THRESH_BINARY);
		threshold(gray, binaryroad, 127, 255, THRESH_BINARY);
		dilate(binaryroad, binaryroad, Mat(), Point(1, 1), 1);

		//imshow("binset", binaryroad);
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				binary.at<uchar>(y, x) = 255 - binary.at<uchar>(y, x);
			}
		}
		Mat edge;
		Mat bincheck;
		bincheck = gray.clone();
		threshold(bincheck, bincheck, 110, 255, THRESH_BINARY);
		Canny(bincheck, bincheck, 130, 210, 3);
		threshold(bincheck, bincheck, 0, 255, CV_THRESH_BINARY_INV);
		erode(bincheck, bincheck, Mat(), Point(1, 1), 1);
		imshow("test edge", bincheck);
		int downx = 0;
		int downindex = 0;
		int downy = 0;
		int downsomethingy = contrastImage.rows;
		bool countforfirst = false;
		bool countformax = false;

		for (int x = (contrastImage.cols / 5 * 2); x < (contrastImage.cols / 5 * 4); x++) {
			for (int y = (contrastImage.rows / 5 * 3); y < contrastImage.rows; y++) {
				if (bincheck.at<uchar>(y, x) == 0)
				{
					if (y < downsomethingy) {
						downsomethingy = y;
						countforfirst = true;
					}
				}
			}
			if (countforfirst) {
				if (abs(downsomethingy - downy) < 4) {
					countformax = true;
				}
				downy = downsomethingy;
			}
			if (countformax) {
				downindex++;
			}
			downsomethingy = contrastImage.rows;
			countformax = false;
			countforfirst = false;
		}
		Canny(gray, edge, 130, 210, 3); // Canny 연산
		threshold(edge, edge, 0, 255, CV_THRESH_BINARY_INV); // sobel 영상과 비교하려고 반전
															 //imshow("Canny Image", edge);

															 /////////////////////////////////////////////////////////////////
															 //2단계 : 블러 처리 -> 인식되는 범위를 늘려줌
		blur(sharpening, blurImage, Size(3, 3));
		//medianBlur(resizeF, resizeF, 5);
		//GaussianBlur(resizeF, resizeF, Size(5, 5), 1.5);


		/////////////////////////////////////////////////////////////////
		//3단계 : MOG2 적용 -> 배경 제거
		//pMOG2->apply(blurImage, MOG2Image);
		//imshow("2. blur + MOG2", MOG2Image);
		Mat closing;
		morphologyEx(blurImage, closing, MORPH_CLOSE, element);
		Mat forstair;
		cvtColor(closing, forstair, CV_RGB2GRAY);
		threshold(forstair, forstair, 230, 255, THRESH_BINARY);
		erode(forstair, forstair, Mat(), Point(1, 1), 5);
		imshow("되라", forstair);
		//imshow("4. closing", closing);
		Mat labeling;
		Mat magic = edge.clone();
		int height = magic.size().height;
		int width = magic.size().width;
		erode(edge, labeling, Mat(), Point(1, 1), 1);
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				if ((binary.at<uchar>(y, x) - labeling.at<uchar>(y, x)) >(150)) {
					magic.at<uchar>(y, x) = (binary.at<uchar>(y, x) - labeling.at<uchar>(y, x));
				}
				else
					magic.at<uchar>(y, x) = 0;
			}
		}
		Mat magic4;
		Mat magic2 = edge.clone();
		//erode(magic, magic, Mat(), Point(1, 1), 1);
		//dilate(magic, magic, Mat(), Point(1, 1), 1);
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				if ((magic.at<uchar>(y, x) - labeling.at<uchar>(y, x)) >(200)) {
					magic2.at<uchar>(y, x) = (magic.at<uchar>(y, x) - labeling.at<uchar>(y, x));
				}
				else
					magic2.at<uchar>(y, x) = 0;
			}
		}
		dilate(magic2, magic2, Mat(), Point(1, 1), 1);
		imshow("blob", magic2);
		int xmar = 0;
		int marindex = 0;
		int xmax = 0;
		for (int y = (contrastImage.rows / 6 * 5); y < contrastImage.rows; y++) {
			for (int x = (contrastImage.cols / 5 * 2); x > 0; x--) {
				if (magic2.at<uchar>(y, x) == 255) {
					if (x > xmar) {
						xmar = x;
					}
				}

			}
		}
		for (int y = (contrastImage.rows / 6 * 5); y < contrastImage.rows; y++) {
			for (int x = (contrastImage.cols / 5 * 2); x < contrastImage.cols; x++) {
				if (magic2.at<uchar>(y, x) == 255) {
					if (x - xmar < marindex) {
						marindex = x - xmar;
						xmax = x;
					}
					if (marindex == 0) {
						marindex = x - xmar;
						xmax = x;
					}
				}
			}
		}
		if (marindex > (xmar + 30)) {
			marindex = marindex - 30;
		}
		if (xmar == 0 || marindex == 0) {
			xmar = (contrastImage.cols / 5 * 2) - 75;
			marindex = 150;
		}
		cv::Mat blank(closing.size(), CV_8U, cv::Scalar(0xFF));
		cv::Mat dest;

		// Create markers image
		Mat markers(closing.size(), CV_8U, cv::Scalar(-1));
		//Rect(topleftcornerX, topleftcornerY, width, height);
		//top rectangle
		markers(Rect(0, 0, closing.cols, 5)) = Scalar::all(1);
		//bottom rectangle
		markers(Rect(0, closing.rows - 5, closing.cols, 5)) = Scalar::all(1);
		//left rectangle
		markers(Rect(0, 0, 5, closing.rows)) = Scalar::all(1);
		//right rectangle
		markers(Rect(closing.cols - 5, 0, 5, closing.rows)) = Scalar::all(1);
		//centre rectangle
		int centreW = closing.cols / 2;
		int centreH = closing.rows / 4;
		markers(Rect(xmar, closing.rows / 6 * 5, marindex, closing.rows / 6)) = Scalar::all(2);
		markers.convertTo(markers, CV_BGR2GRAY);
		imshow("markers", markers);
		//Create watershed segmentation object
		WatershedSegmenter segmenter;
		segmenter.setMarkers(markers);
		cv::Mat wshedMask = segmenter.process(closing);
		cv::Mat mask;
		Mat eroding;

		convertScaleAbs(wshedMask, mask, 1, 0);
		double thresh = threshold(mask, mask, 1, 255, THRESH_BINARY);
		erode(closing, eroding, Mat(), Point(1, 1), 1);
		//imshow("label", labeling);


		// Uncomment the line below to select a different bounding box 
		// bbox = selectROI(frame, false); 
		// Display bounding box. 
		//imshow("erode", eroding);
		bitwise_and(closing, eroding, dest, mask);
		dest.convertTo(dest, CV_8U);
		imshow("final_result", dest);
		Mat something;
		cvtColor(dest, something, CV_RGB2GRAY);
		Mat hard;
		hard = magic2.clone();
		int count = 0;
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				if (something.at<uchar>(y, x) == 0) {
					magic2.at<uchar>(y, x) = 0;
				}
			}
		}
		int countmax = contrastImage.cols / 4 * 2 - contrastImage.rows / 5 * 2;
		int k = 0;
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				k = abs(contrastImage.rows - y);
				if (x < (contrastImage.cols / 5) + k / 2) {
					hard.at<uchar>(y, x) = 0;
				}
				else if (x >(contrastImage.cols / 5 * 4) - k / 2) {
					hard.at<uchar>(y, x) = 0;
				}
				else if (y < contrastImage.rows / 5*3){
					hard.at<uchar>(y, x) = 0;
				}
				if (y == (contrastImage.rows / 5 * 3) - 1) {

				}
			}
		}
		Mat calculatemov;
		calculatemov = closing.clone();
		int tmpmov = 0;
		for (int y = 0; y < contrastImage.rows /5*4; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				if (hard.at < uchar>(y, x) != 0) {
					count++;
					tmpmov = tmpmov + y + abs(x - contrastImage.cols / 2);
				}
				else {
					calculatemov.at<Vec3b>(y, x)[0] = 0;
					calculatemov.at<Vec3b>(y, x)[1] = 0;
					calculatemov.at<Vec3b>(y, x)[2] = 0;
				}
			}
		}
		int movcounter = 0;
		if (saveset) {
			for (int y = 0; y < contrastImage.rows; y++) {
				for (int x = 0; x < contrastImage.cols; x++) {
					if (savemov.at<uchar>(y, x) != 0) {
						calculatemov.at<Vec3b>(y, x)[0] = 0;
						calculatemov.at<Vec3b>(y, x)[1] = 0;
						calculatemov.at<Vec3b>(y, x)[2] = 0;
					}
					//else if (calculatemov.at<Vec3b>(y, x)[0] != 0 || calculatemov.at<Vec3b>(y, x)[1] != 0 || calculatemov.at<Vec3b>(y, x)[2] != 0) {
					//movcounter++;
					//}
				}
			}
			if (count != 0) {
				tmpmov = (tmpmov / count) * 5;
			}
			movcounter = tmpmov;
		}
		else if (count != 0) {
			tmpmov = (tmpmov / count) * 5;
		}
		cvtColor(calculatemov, savemov, CV_RGB2GRAY);
		saveset = true;
		//imshow("test", calculatemov);
		if (count > 100) {
			errorhandle++;
		}
		else if (errorhandle > 5) {
			errorhandle = 5;
		}
		else if (errorhandle>0) {
			errorhandle--;
		}
		else if(errorhandle <3){
			obf =0;
		}

		if (errorhandle > 3) {
			if (movcount != 0 && count > 100) {
				if (movcounter > tmpmov * 2) {
					if (downhandle > 5) {
						if (count < 70 && stairbit ==false && downbit == false) {
							if(obf == 0){
								recv_message[0] = 'o';
								write_server(client, recv_message);
						}
						obf ++;
							}
					}
					else if(stairbit ==false && downbit == false){
						if(obf ==0){
							recv_message[0] = 'c';
							write_server(client, recv_message);
					}	
					obf ++;
					}
				}
				else {
					if (downhandle > 5) {
						if (count < 70 && stairbit ==false && downbit == false) {
							if(obf ==0){
								recv_message[0] = 'o';
								write_server(client, recv_message);
						}
						obf++;
						}
					}
					else if(stairbit ==false && downbit == false){
						if(obf ==0){
							recv_message[0] = 'o';
							write_server(client, recv_message);
					}
					obf++;
					}
				}
			}
			else if (count > 70 && stairbit ==false && downbit == false) {
				if(obf ==0){
					recv_message[0] = 'o';
					write_server(client, recv_message);
			}
			obf++;
			}
			if(downbit && count < 140 && count >70){
				if(obf ==0){
					recv_message[0] = 'o';
					write_server(client, recv_message);
			}
			obf++;
			}
		}
		movcounter = 0;
		movcount = tmpmov;
		int cross = 0;
		bool largestbit = false;
		for (int y = 0; y < contrastImage.rows; y++) {
			for (int x = 0; x < contrastImage.cols; x++) {
				if (something.at<uchar>(y, x) != 0) {
					if (!largestbit) {
						cross = y;
						largestbit = true;
					}
				}
			}
		}
		if (cross > contrastImage.rows / 5 * 3) {
			noisehandle++;
		}
		else if (noisehandle > 4) {
			noisehandle = 4;
		}
		else if (noisehandle>0) {
			noisehandle--;
		}
		if (test_flag == 1 ) {
			if (noisehandle > 4 || stairhandle > 6 ) {
					recv_message[0] = 'r';
					write_server(client, recv_message);
					//Camera.release();
					int ret = system("python3 opencv_save.py");
					char buffer[10];
					test_flag =0;
					return 0;
			}
		}
		else if(noisehandle <2){
			test_flag = 0;
		}
		int downstair = 0;
		int countforstair = 0;
		for (int y = 1; y < contrastImage.rows; y++) {
			int x = contrastImage.cols/2;
			if (bincheck.at<uchar>(y, x) == 255 && bincheck.at<uchar>(y - 1, x) != 255) {
				if(bincheck.at<uchar>(y, x-2) == 255 && bincheck.at<uchar>(y, x+2) == 255){
						if(bincheck.at<uchar>(y, x-1) = 255 && bincheck.at<uchar>(y, x+1) == 255){
						countforstair++;
					}
				}
					}
			}
		if (countforstair > 2) {
			stairhandle++;
		}
		else if (stairhandle>8) {
			stairhandle = 8;
		}
		else if (stairhandle>0) {
			stairhandle--;
		}
		if (downindex > 120 ) {
			downhandle++;
		}
		else if (downhandle > 15) {
			downhandle = 15;
		}
		else if (downhandle>0) {
			downhandle--;
		}
		else if(downhandle<1){
			dof =0;
		}
		if(stairbit == false){
			upf =0;
		}
			
		if (stairhandle > 8  && downbit == false && downhandle>6 && noisehandle>3) {

			stairbit = true;
			stairbitcheck = 0;
		}
		else if(stairhandle > 3 && downbit == false && downhandle>6 && noisehandle>3){
			stairbitcheck =0;
		}
		else if (noisehandle > 3 && stairbit == true) {
			stairbitcheck--;
		}
		else if (stairbitcheck > 15 && downhandle <3 && noisehandle<3) {
			stairbit = false;
			stairbitcheck =0;
		}
		else {
			stairbitcheck++;
		}
		if (stairbit) {
			if (upf == 0) {
							if(test_flag ==0){
				recv_message[0] = 'u';
				write_server(client, recv_message);
			}
			}
			upf++;
		}
		else if (downhandle > 30 && stairbit == false) {
			noisehandle = 0;
			downbit = true;
			downstairbit = 1;
			if(dof ==0){
							if(test_flag ==0){
				recv_message[0] = 'd';
				write_server(client, recv_message);
			}
		}
		dof++;
		}
		else if (downbit && stairbit == false) {
		dof++;
		}
		if (downstairbit > 0) {
			downstairbit++;
		}
		if (downstairbit > 1500) {
			downstairbit = 0;
			downbit = false;
		}
		imshow("..", hard);
		//imshow("gray", something);
		//imshow("calculated", magic2);

		if (waitKey(30) >= 0)
			return 1;
		if (recv_message == NULL) {
			printf("client disconnected\n");
			break;
		}
		if(dof > 50){
			dof =0;
		}
		if(upf > 50){
			upf =0;
		}
		if(obf >50){
			obf =0;
		}
	}
	return 0;
}

int main() {
	int i = 0;
	int client = init_server();
	while (1) {
		image_process(client);
		if (waitKey(30) >= 0) {
			break;
		}
	}
	return 0;
}
