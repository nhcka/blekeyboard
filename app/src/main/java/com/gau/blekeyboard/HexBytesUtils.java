package com.gau.blekeyboard;

import java.util.Locale;

public class HexBytesUtils {
	private static final String qppHexStr = "0123456789ABCDEF";

	/**
	 * Hex char convert to byte array
	 * 
	 * @version 1.0
	 * @createTime 2014-3-21,PM2:16:38
	 * @updateTime 2014-3-21,PM2:16:38
	 * @createAuthor Qn Sw team
	 * @updateAuthor Qn Sw team
	 * @updateInfo
	 * 
	 * @param paramString
	 * @return
	 */
	public static byte[] hexStr2Bytes(String hexString) {
		/*
		 * int len = paramString.length()/2; byte[] mbytes = new byte[len]; for(int i=0;i<len;i++){ mbytes[i] =
		 * (byte)Integer.parseInt(paramString.substring(i*2, i*2+2), 16); } return mbytes;
		 */

		if (hexString == null || hexString.isEmpty()) {
			return null;
		}

		hexString = hexString.toUpperCase(Locale.US);

		int length = hexString.length() >> 1;
		char[] hexChars = hexString.toCharArray();

		int i = 0;

		do {
			int checkChar = qppHexStr.indexOf(hexChars[i]);

			if (checkChar == -1)
				return null;
			i++;
		} while (i < hexString.length());

		byte[] dataArr = new byte[length];

		for (i = 0; i < length; i++) {
			int strPos = i * 2;

			dataArr[i] = (byte) (charToByte(hexChars[strPos]) << 4 | charToByte(hexChars[strPos + 1]));
		}

		return dataArr;
	}

	private static byte charToByte(char c) {
		return (byte) qppHexStr.indexOf(c);
	}
	
	public static String bytes2HexStr(byte[] buf) {
		if (buf == null)
			return "";
		StringBuffer result = new StringBuffer(2 * buf.length);
		for (int i = 0; i < buf.length; i++) {
			appendHex(result, buf[i]);
		}
		return result.toString();
	}

	private static void appendHex(StringBuffer sb, byte b) {
		sb.append(qppHexStr.charAt((b >> 4) & 0x0f)).append(qppHexStr.charAt(b & 0x0f));
	}
}
