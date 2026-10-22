package com.krishna.apphub

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date

object Signing {
    data class Result(val p12: ByteArray, val pass: String, val alias: String)

    fun generate(cn: String): Result {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=$cn, O=AppHub")
        val from = Date()
        val to = Date(from.time + 36500L * 24 * 3600 * 1000)
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(from.time), from, to, name, kp.public)
            .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val pass = (1..24).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry("release", kp.private, pass.toCharArray(), arrayOf(cert))
        val out = ByteArrayOutputStream()
        ks.store(out, pass.toCharArray())
        return Result(out.toByteArray(), pass, "release")
    }
}

object Sodium {
    private val ls by lazy { LazySodiumAndroid(SodiumAndroid()) }

    /** Sealed-box encrypt [value] with the repo public key (base64), as GitHub secrets require. */
    fun seal(publicKeyB64: String, value: String): String {
        val pk = Base64.decode(publicKeyB64, Base64.DEFAULT)
        val msg = value.toByteArray()
        val cipher = ByteArray(Box.SEALBYTES + msg.size)
        check(ls.cryptoBoxSeal(cipher, msg, msg.size.toLong(), pk)) { "seal failed" }
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }
}
