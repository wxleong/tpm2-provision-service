package com.infineon.tpm20.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public class MiscUtil {
    public static String byteArrayToBase64(byte[] ba) {
        return Base64.getEncoder().encodeToString(ba);
    }

    public static String stringToBase64(String string) {
        return Base64.getEncoder().encodeToString(string.getBytes());
    }

    public static byte[] base64ToByteArray(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public static String base64ToString(String base64) {
        byte[] ba = Base64.getDecoder().decode(base64);
        return new String(ba);
    }

    public static String objectToJson(Object obj) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T JsonToObject(String json, Class<T> className) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, className);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toHexString(int i) {
        return "0x" + Integer.toHexString(i).toUpperCase();
    }
}
