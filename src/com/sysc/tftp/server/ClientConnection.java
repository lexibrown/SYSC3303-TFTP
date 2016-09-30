package com.sysc.tftp.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

import com.sysc.tftp.utils.Logger;
import com.sysc.tftp.utils.Variables;
import com.sysc.tftp.utils.Variables.Request;

public class ClientConnection implements Runnable {

	// UDP datagram packets and socket used to send / receive
	private DatagramPacket receivePacket = null, sendPacket = null;
	private DatagramSocket sendReceiveSocket = null;

	private byte[] data = null; // holds the original request
	private byte[] fileBytes = null; // hold bytes of file to transfer

	// client information: port, IP, length of data
	private int len = 0, clientPort = 0;
	private InetAddress clientIP = null;

	public ClientConnection(byte[] data, int len, InetAddress ip, int port) {
		this.data = data;
		this.len = len;
		this.clientIP = ip;
		this.clientPort = port;
	}

	/**
	 * Handles request
	 */
	@Override
	public void run() {
		byte[] response = null;
		String filename = null;

		Request req = verifyRequest(data);
		if (req == null || req == Request.ERROR) {
			// TODO
			// issue (iteration 2)
		}

		filename = Variables.SERVER_FILES_DIR + pullFilename(data);

		// Create a response.
		if (req == Request.RRQ) {
			if (fileBytes == null) {
				File f = new File(filename);
				if (!f.exists() || f.isDirectory()) {
					// TODO
					// issue, file does not exist
					// iteration 2
				}
				fileBytes = new byte[(int) f.length()];
				try {
					FileInputStream fis = new FileInputStream(filename);
					fis.read(fileBytes);
					fis.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			response = packageRead();
		} else if (req == Request.WRQ) {
			response = Variables.ACK;
		}

		sendPacket = new DatagramPacket(response, response.length, clientIP, clientPort);

		Logger.logPacketSending(sendPacket);

		try {
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.send(sendPacket);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		Logger.log("Server: packet sent using port " + sendReceiveSocket.getLocalPort());
		Logger.log("");

		if (req == Request.RRQ && fileBytes != null && response.length < Variables.MAX_PACKET_SIZE) {
			fileBytes = null;
			// We're finished with this socket, so close it.
			Logger.log("Closing socket...");
			sendReceiveSocket.close();
			Logger.log("Thread done.");
			return;
		}

		while (true) {

			byte[] received = new byte[Variables.MAX_PACKET_SIZE];
			receivePacket = new DatagramPacket(received, received.length);

			Logger.log("Server: Waiting for packet.");
			try {
				// Block until a datagram is received via sendReceiveSocket.
				sendReceiveSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Process the received datagram.
			Logger.logPacketReceived(receivePacket);

			if (req == Request.RRQ) {
				if (verifyACK(received)) {
					response = packageRead();
				} else {
					// TODO
					// issue, request is not correct
					// iteration 2
					break;
				}
			} else if (req == Request.WRQ) {
				if (verifyDATA(received)) {
					writeToFile(filename, Arrays.copyOfRange(received, Variables.DATA.length, received.length));
					response = Variables.ACK;
				} else {
					// TODO
					// issue, request is not correct
					// iteration 2
					break;
				}
			}

			sendPacket = new DatagramPacket(response, response.length, clientIP, clientPort);
			Logger.logPacketSending(sendPacket);

			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			Logger.log("Server: packet sent using port " + sendReceiveSocket.getLocalPort());
			Logger.log("");

			if (req == Request.RRQ && fileBytes != null && response.length < Variables.MAX_PACKET_SIZE) {
				fileBytes = null;
				break;
			} else if (req == Request.WRQ && received.length < Variables.MAX_PACKET_SIZE) {
				break;
			}
		}

		// We're finished with this socket, so close it.
		Logger.log("Closing socket...");
		sendReceiveSocket.close();
		Logger.log("Thread done.");
	}

	/**
	 * Packages file to be read
	 * 
	 * @return Part, if not all, of file
	 */
	public byte[] packageRead() {
		byte[] finalPackage = null;
		if (fileBytes.length > Variables.MAX_PACKET_SIZE - Variables.DATA.length) {
			finalPackage = new byte[Variables.MAX_PACKET_SIZE];
			System.arraycopy(Variables.DATA, 0, finalPackage, 0, Variables.DATA.length);
			System.arraycopy(fileBytes, 0, finalPackage, Variables.DATA.length,
					Variables.MAX_PACKET_SIZE - Variables.DATA.length);
			fileBytes = Arrays.copyOfRange(fileBytes, Variables.MAX_PACKET_SIZE - Variables.DATA.length,
					fileBytes.length);
		} else {
			finalPackage = new byte[Variables.DATA.length + fileBytes.length];
			System.arraycopy(Variables.DATA, 0, finalPackage, 0, Variables.DATA.length);
			System.arraycopy(fileBytes, 0, finalPackage, Variables.DATA.length, fileBytes.length);
		}
		return finalPackage;
	}

	/**
	 * Writes to specified file, if file doesn't exist create new file
	 * 
	 * @param filename
	 *            File to find/create and write to
	 * @param fileContent
	 *            Content to put in file
	 */
	public void writeToFile(String filename, byte[] fileContent) {
		try {
			File f = new File(filename);
			if (!f.exists()) {
				f.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(f, true);
			fos.getFD().sync();
			fos.write(fileContent);
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Verifies that the first 4 bytes are ACK
	 * 
	 * @param data
	 *            Message received
	 * @return if proper ACK message
	 */
	public boolean verifyACK(byte[] data) {
		// TODO temp -2 minus, will fix later
		for (int i = 0; i < Variables.ACK.length - 2; i++) {
			if (Variables.ACK[i] != data[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Verifies that the first 4 bytes are DATA
	 * 
	 * @param data
	 *            Message received
	 * @return if proper DATA message
	 */
	public boolean verifyDATA(byte[] data) {
		if (data.length <= Variables.DATA.length) { // no data in message
			return false;
		}
		for (int i = 0; i < Variables.DATA.length; i++) {
			if (Variables.DATA[i] != data[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Verifies that the request is valid
	 * 
	 * @param data
	 *            Request sent
	 * @return Enum of type of request
	 */
	public Request verifyRequest(byte[] data) {
		Request req; // READ, WRITE or ERROR
		int j = 0, k = 0;

		if (data[0] != 0) {
			return Request.ERROR; // bad
		} else if (data[1] == 1) {
			req = Request.RRQ; // could be read
		} else if (data[1] == 2) {
			req = Request.WRQ; // could be write
		} else {
			return Request.ERROR; // bad
		}

		if (req != Request.ERROR) { // check for filename
			// search for next all 0 byte
			for (j = 2; j < len; j++) {
				if (data[j] == 0) {
					break;
				}
			}
			if (j == len) {
				return Request.ERROR; // didn't find a 0 byte
			}
			if (j == 2) {
				return Request.ERROR; // filename is 0 bytes long
			}
		}

		if (req != Request.ERROR) { // check for mode
			// search for next all 0 byte
			for (k = j + 1; k < len; k++) {
				if (data[k] == 0) {
					break;
				}
			}
			if (k == len) {
				return Request.ERROR; // didn't find a 0 byte
			}
			if (k == j + 1) {
				return Request.ERROR; // mode is 0 bytes long
			}
		}

		if (k != len - 1) {
			return Request.ERROR; // other stuff at end of packet
		}

		return req;
	}

	/**
	 * Returns the name of the file
	 * 
	 * @param data
	 *            Contains filename
	 * @return filename
	 */
	public String pullFilename(byte[] data) {
		int j;
		for (j = 2; j < len; j++) {
			if (data[j] == 0)
				break;
		}
		return new String(data, 2, j - 2);
	}

}