/**
 *
 * todo: broadcast/receive broadcast.
 */

/**
 * @author Quintic Zhang Fuquan
 *
 */
package com.gau.blekeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

/**
 * @Description QPP API / CallBack functions
 * @author fqZhang
 * @version 1.0
 * @date 2014-7-10
 * @Copyright (c) 2014 Quintic Co., Ltd. Inc. All rights reserved.
 *
 */

@SuppressLint("NewApi")
public class QppApi {
	private static ArrayList<BluetoothGattCharacteristic> arrayNtfCharList = new ArrayList<BluetoothGattCharacteristic>();

	// / send data
	private static BluetoothGattCharacteristic writeCharacteristic; // / write Characteristic
	// private static String uuidQppService = "0000fee9-0000-1000-8000-00805f9b34fb";
	// private static String uuidQppCharWrite = "d44bc439-abfd-45a2-b575-925416129600";
	public static final int qppServerBufferSize = 20;
	// / receive data
	// private static BluetoothGattCharacteristic notifyCharacteristic;
	/** notify Characteristic */
	private static byte notifyCharaIndex = 0;
	private static boolean NotifyEnabled = false;
	private static final String UUIDDes = "00002902-0000-1000-8000-00805f9b34fb";
	private static String TAG = QppApi.class.getSimpleName();

	private static iQppCallback iQppCallback;

	public static void setCallback(iQppCallback mCb) {
		iQppCallback = mCb;
	}

	public static void updateValueForNotification(BluetoothGatt bluetoothGatt,
			BluetoothGattCharacteristic characteristic) {
		if (bluetoothGatt == null || characteristic == null) {
			Log.e(TAG, "invalid arguments");
			return;
		}
		if (!NotifyEnabled) {
			Log.e(TAG, "The notifyCharacteristic not enabled");
			return;
		}
		String strUUIDForNotifyChar = characteristic.getUuid().toString();
		final byte[] qppData = characteristic.getValue();
		if (qppData != null && qppData.length > 0)
			iQppCallback.onQppReceiveData(bluetoothGatt, strUUIDForNotifyChar, qppData);
	}

	private static void resetQppField() {
		writeCharacteristic = null;
		// notifyCharacteristic = null;

		arrayNtfCharList.clear();
		// NotifyEnabled=false;
		notifyCharaIndex = 0;
	}

	public static boolean qppEnable(BluetoothGatt bluetoothGatt, String qppServiceUUID, String writeCharUUID) {
		resetQppField();
		if (qppServiceUUID != null)
			// uuidQppService = qppServiceUUID;
			if (writeCharUUID != null)
				// uuidQppCharWrite = writeCharUUID;
				if (bluetoothGatt == null || qppServiceUUID.isEmpty() || writeCharUUID.isEmpty()) {
					Log.e(TAG, "invalid arguments");
					return false;
				}
		BluetoothGattService qppService = bluetoothGatt.getService(UUID.fromString(qppServiceUUID));
		if (qppService == null) {
			Log.e(TAG, "Qpp service not found");
			return false;
		}
		List<BluetoothGattCharacteristic> gattCharacteristics = qppService.getCharacteristics();
		for (int j = 0; j < gattCharacteristics.size(); j++) {
			BluetoothGattCharacteristic chara = gattCharacteristics.get(j);
			if (chara.getUuid().toString().equals(writeCharUUID)) {
				// Log.i(TAG,"Wr char is "+chara.getUuid().toString());
				writeCharacteristic = chara;
			} else if (chara.getProperties() == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
				// Log.i(TAG,"NotiChar UUID is : "+chara.getUuid().toString());
				// notifyCharacteristic = chara;
				arrayNtfCharList.add(chara);
			}
		}

		if (!setCharacteristicNotification(bluetoothGatt, arrayNtfCharList.get(0), true))
			return false;
		notifyCharaIndex++;

		return true;
	}

	// / data sent
	public static boolean qppSendData(BluetoothGatt bluetoothGatt, byte[] qppData) {
		if (bluetoothGatt == null) {
			Log.e(TAG, "BluetoothAdapter not initialized !");
			return false;
		}

		if (qppData == null) {
			Log.e(TAG, "qppData = null !");
			return false;
		}
		return writeValue(bluetoothGatt, writeCharacteristic, qppData);
	}

	public static void PrintBytes(byte[] bytes) {
		if (bytes == null)
			return;
		final StringBuilder stringBuilder = new StringBuilder(bytes.length);
		for (byte byteChar : bytes)
			stringBuilder.append(String.format("%02X ", byteChar));
		Log.i(TAG, " :" + stringBuilder.toString());
	}

	private static boolean writeValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] bytes) {
		if (gatt == null) {
			Log.e(TAG, "BluetoothAdapter not initialized");
			return false;
		}
		// PrintBytes(bytes);
		characteristic.setValue(bytes);
		return gatt.writeCharacteristic(characteristic);
	}

	public static boolean setQppNextNotify(BluetoothGatt bluetoothGatt, boolean EnableNotifyChara) {
		if (notifyCharaIndex == arrayNtfCharList.size()) {
			NotifyEnabled = true;
			return true;
		}
		return setCharacteristicNotification(bluetoothGatt, arrayNtfCharList.get(notifyCharaIndex++), EnableNotifyChara);
	}

	private static boolean setCharacteristicNotification(BluetoothGatt bluetoothGatt,
			BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (bluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return false;
		}

		bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		try {
			BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(UUIDDes));
			characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
			if (descriptor != null) {
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				return (bluetoothGatt.writeDescriptor(descriptor));
			} else {
				Log.e(TAG, "descriptor is null");
				return false;
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return true;
	}
}
