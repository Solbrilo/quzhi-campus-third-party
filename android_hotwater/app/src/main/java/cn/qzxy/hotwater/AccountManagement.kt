package cn.qzxy.hotwater

data class AccountProfile(val name: String = "", val school: String = "")
data class AccountActionState(val busy: Boolean = false, val sending: Boolean = false,
    val remaining: Int = 0, val error: String? = null, val success: Boolean = false)

internal fun accountProfile(user: org.json.JSONObject, project: org.json.JSONObject): AccountProfile {
    val data = user.optJSONObject("data") ?: org.json.JSONObject()
    val school = project.optJSONObject("data") ?: org.json.JSONObject()
    return AccountProfile(listOf("userName", "name", "nickName").firstNotNullOfOrNull {
        data.optString(it).takeIf { value -> value.isNotBlank() && value != "null" }
    }.orEmpty(), school.optString("projectName").takeUnless { it == "null" }.orEmpty())
}
