package com.unoxidized.shared
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase over plain HTTPS (Auth + Firestore REST). No SDK and no Gradle plugin, so it cannot break the build.
 * The API key below only identifies the Firebase project (it is not a secret). WHO may read or write WHAT is decided by the Firestore
 * security rules in firestore.rules on Google's servers, not by this app.
 */
object Cloud {
    const val PROJECT = "nacht-x"
    const val API_KEY = "AIzaSyDtC3An39eL1_4bZ0rSU1XE6a1Y4f8jGVg"
    private const val DOCS = "projects/$PROJECT/databases/(default)/documents"

    class Session(val uid: String, val email: String, var idToken: String, var refresh: String, var expiresAt: Long)
    class Res(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
        fun json(): JSONObject = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        fun msg(): String {
            if (code == -1) return "No internet connection."
            val m = try { JSONObject(body).optJSONObject("error")?.optString("message", "") ?: "" } catch (e: Exception) { "" }
            return when {
                m.startsWith("EMAIL_EXISTS") -> "That email already has an account. Use Sign in."
                m.startsWith("INVALID_LOGIN_CREDENTIALS") || m.startsWith("INVALID_PASSWORD") || m.startsWith("EMAIL_NOT_FOUND") -> "Wrong email or password."
                m.startsWith("WEAK_PASSWORD") -> "Password must be at least 6 characters."
                m.startsWith("INVALID_EMAIL") -> "That email address isn't valid."
                m.startsWith("OPERATION_NOT_ALLOWED") -> "Email/Password sign-in is not enabled in Firebase yet."
                m.startsWith("TOO_MANY_ATTEMPTS") -> "Too many attempts. Wait a few minutes and try again."
                m.startsWith("USER_DISABLED") -> "This account was disabled."
                code == 403 -> "The server refused this (permission denied). The owner must publish the Firestore rules."
                code == 409 || m.contains("ALREADY_EXISTS") -> "That entry already exists."
                m.isNotBlank() -> m.take(80)
                else -> "Server error ($code)."
            }
        }
    }
    @Volatile var session: Session? = null

    private fun http(method: String, url: String, body: String?, token: String?, form: Boolean = false): Res = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val txt = try { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
        Res(code, txt)
    } catch (e: Exception) { Res(-1, e.message ?: "network error") }

    // ---------- Authentication ----------
    private fun auth(endpoint: String, email: String, pw: String): Pair<Session?, String> {
        val r = http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:$endpoint?key=$API_KEY", JSONObject().put("email", email).put("password", pw).put("returnSecureToken", true).toString(), null)
        if (!r.ok) return Pair(null, r.msg())
        val j = r.json()
        return Pair(Session(j.optString("localId"), email, j.optString("idToken"), j.optString("refreshToken"), System.currentTimeMillis() + j.optString("expiresIn", "3600").toLong() * 1000L), "")
    }
    fun signUp(email: String, pw: String) = auth("signUp", email, pw)
    fun signIn(email: String, pw: String) = auth("signInWithPassword", email, pw)
    fun keep(sp: SharedPreferences, s: Session) { session = s; sp.edit().putString("cl_rt", s.refresh).putString("cl_uid", s.uid).putString("cl_email", s.email).apply() }
    fun forget(sp: SharedPreferences) { session = null; sp.edit().remove("cl_rt").remove("cl_uid").remove("cl_email").apply() }
    fun signedIn(sp: SharedPreferences) = (sp.getString("cl_rt", "") ?: "").isNotEmpty()

    /** Returns a session with a fresh token, or null if the user is not signed in or the server cannot be reached. */
    fun restore(sp: SharedPreferences): Session? {
        val cur = session
        if (cur != null && cur.expiresAt - 60000L > System.currentTimeMillis()) return cur
        val rt = (if (cur != null) cur.refresh else sp.getString("cl_rt", "")) ?: ""
        if (rt.isEmpty()) return null
        val r = http("POST", "https://securetoken.googleapis.com/v1/token?key=$API_KEY", "grant_type=refresh_token&refresh_token=$rt", null, true)
        if (!r.ok) { if (r.code == 400) forget(sp); return null }
        val j = r.json(); val uid = j.optString("user_id", sp.getString("cl_uid", "") ?: "")
        val s = Session(uid, sp.getString("cl_email", "") ?: "", j.optString("id_token"), j.optString("refresh_token", rt), System.currentTimeMillis() + j.optString("expires_in", "3600").toLong() * 1000L)
        keep(sp, s); return s
    }

    // ---------- Firestore ----------
    private fun fv(v: Any?): JSONObject = when (v) {
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int -> JSONObject().put("integerValue", v.toString())
        is Long -> JSONObject().put("integerValue", v.toString())
        else -> JSONObject().put("stringValue", v?.toString() ?: "")
    }
    private fun fields(m: Map<String, Any?>): JSONObject { val o = JSONObject(); for ((k, v) in m) o.put(k, fv(v)); return o }
    fun decode(doc: JSONObject): Map<String, String> {
        val out = HashMap<String, String>(); val f = doc.optJSONObject("fields") ?: return out; val ks = f.keys()
        while (ks.hasNext()) { val k = ks.next(); val v = f.optJSONObject(k) ?: continue; out[k] = if (v.has("stringValue")) v.optString("stringValue") else if (v.has("integerValue")) v.optString("integerValue") else if (v.has("booleanValue")) v.optString("booleanValue") else "" }
        return out
    }
    fun getDoc(s: Session, path: String): Res = http("GET", "https://firestore.googleapis.com/v1/$DOCS/$path", null, s.idToken)

    /** One write for commit(). create = true fails if the document already exists; false updates only the given fields of an existing document. */
    fun op(path: String, data: Map<String, Any?>, create: Boolean): JSONObject {
        val w = JSONObject().put("update", JSONObject().put("name", "$DOCS/$path").put("fields", fields(data)))
        if (create) w.put("currentDocument", JSONObject().put("exists", false))
        else { w.put("updateMask", JSONObject().put("fieldPaths", JSONArray(data.keys.toList()))); w.put("currentDocument", JSONObject().put("exists", true)) }
        return w
    }
    /** All writes succeed together or none do. */
    fun commit(s: Session, ops: List<JSONObject>): Res {
        val a = JSONArray(); for (o in ops) a.put(o)
        return http("POST", "https://firestore.googleapis.com/v1/$DOCS:commit", JSONObject().put("writes", a).toString(), s.idToken)
    }
    /** Lists documents in a collection, optionally where field == value. Returns (documentId, fields) pairs, or null on error. */
    fun query(s: Session, collection: String, field: String?, value: String?, limit: Int): List<Pair<String, Map<String, String>>>? {
        val q = JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection))).put("limit", limit)
        if (field != null && value != null) q.put("where", JSONObject().put("fieldFilter", JSONObject().put("field", JSONObject().put("fieldPath", field)).put("op", "EQUAL").put("value", JSONObject().put("stringValue", value))))
        val r = http("POST", "https://firestore.googleapis.com/v1/$DOCS:runQuery", JSONObject().put("structuredQuery", q).toString(), s.idToken)
        if (!r.ok) return null
        val out = ArrayList<Pair<String, Map<String, String>>>()
        try { val a = JSONArray(r.body); for (i in 0 until a.length()) { val d = a.getJSONObject(i).optJSONObject("document") ?: continue; out.add(Pair(d.optString("name").substringAfterLast('/'), decode(d))) } } catch (e: Exception) { return null }
        return out
    }
}
