package com.atpexgo.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * decryption util class
 * Created by atpex on 2018/1/12.
 */
public class DecryptionUtil {

    /**
     * decrypt response data
     * @param data data field
     * @param iv iv field
     * @param key secret key field
     * @return decrypted data formed in JSONObject
     * @throws Exception bla bla
     */
    public static JSONObject decrypt(String data, String iv, String key) throws Exception {
        SecretKeySpec paramArrayOfByte = new SecretKeySpec(decodeRSA(getBytes(key)), "AES");
        Cipher localCipher = Cipher.getInstance("AES/CBC/NoPadding");
        localCipher.init(2, paramArrayOfByte, new IvParameterSpec(getBytes(iv)));
        byte[] des = localCipher.doFinal(getBytes(data));
        return JSONObject.parseObject(new String(des));
    }

    /**
     * decode BASE64 data to byte array
     * @param origin original BASE64 data
     * @return byte array
     * @throws Exception bla bla
     */
    private static byte[] getBytes(String origin) throws Exception {
        return Base64.decodeBase64(origin);
    }

    /**
     * decode RSA-encrypted data to byte array
     * @param paramArrayOfByte RSA-encrypted data
     * @return byte array
     * @throws Exception bla bla
     */
    private static byte[] decodeRSA(byte[] paramArrayOfByte) throws Exception {
        PrivateKey privateKey = getPrivateKey(parsePrivateKey(DecryptionUtil.class.getClassLoader().getResourceAsStream("static/pkcs8_private_key.pem")));
        Cipher localCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        localCipher.init(2, privateKey);
        paramArrayOfByte = localCipher.doFinal(paramArrayOfByte);
        return paramArrayOfByte;
    }

    /**
     * generate RSA PrivateKey object with string-formed key
     * @param paramString string-formed key
     * @return RSA PrivateKey object
     * @throws Exception bla bla
     */
    private static PrivateKey getPrivateKey(String paramString) throws Exception {
        byte[] arrayOfByte = getBytes(paramString);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(arrayOfByte));
    }

    /**
     * parse RSA private key file to string
     * @param paramInputStream RSA private key input stream
     * @return string-formed key
     * @throws IOException bla bla
     */
    private static String parsePrivateKey(InputStream paramInputStream) throws IOException {
        BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(paramInputStream));
        StringBuilder localStringBuilder = new StringBuilder();
        for (; ; ) {
            String paramInput = localBufferedReader.readLine();
            if (paramInput == null) {
                break;
            }
            if (paramInput.charAt(0) != '-') {
                localStringBuilder.append(paramInput);
                localStringBuilder.append('\r');
            }
        }
        return localStringBuilder.toString();
    }

    /**
     * string to MD5
     * @param src string
     * @return md5
     */
    public static String md5(String src) {
        // md5 string
        char md5String[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9','A', 'B', 'C', 'D', 'E', 'F'};
        try {
            // get byte array
            byte[] btInput = src.getBytes();
            // single-way hash method md5
            MessageDigest mdInst = MessageDigest.getInstance("md5");
            // update data
            mdInst.update(btInput);
            // calculate md5 hash code
            byte[] md = mdInst.digest();
            // transform to HEX
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) {   //  i = 0
                str[k++] = md5String[byte0 >>> 4 & 0xf];    //    5
                str[k++] = md5String[byte0 & 0xf];   //   F
            }
            // encrypted string(lower case)
            return new String(str).toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}
