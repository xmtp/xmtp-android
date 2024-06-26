package org.xmtp.android.library.messages

sealed class Topic {
    data class userPrivateStoreKeyBundle(val address: String?) : Topic()
    data class contact(val address: String?) : Topic()
    data class userIntro(val address: String?) : Topic()
    data class userInvite(val address: String?) : Topic()
    data class directMessageV1(val address1: String?, val address2: String?) : Topic()
    data class directMessageV2(val addresses: String?) : Topic()
    data class preferenceList(val identifier: String?) : Topic()
    data class userWelcome(val installationId: String?) : Topic()
    data class groupMessage(val groupId: String?) : Topic()

    /**
     * Getting the [Topic] structured depending if is [userPrivateStoreKeyBundle], [contact],
     * [userIntro], [userInvite], [directMessageV1], [directMessageV2] and [preferenceList]
     * with the structured string as /xmtp/0/{id}/proto
     */
    val description: String
        get() {
            return when (this) {
                is userPrivateStoreKeyBundle -> wrap("privatestore-$address/key_bundle")
                is contact -> wrap("contact-$address")
                is userIntro -> wrap("intro-$address")
                is userInvite -> wrap("invite-$address")
                is directMessageV1 -> {
                    val addresses = arrayOf(address1, address2)
                    addresses.sort()
                    wrap("dm-${addresses.joinToString(separator = "-")}")
                }

                is directMessageV2 -> wrap("m-$addresses")
                is preferenceList -> wrap("userpreferences-$identifier")
                is groupMessage -> wrapMls("g-$groupId")
                is userWelcome -> wrapMls("w-$installationId")
            }
        }

    private fun wrap(value: String): String = "/xmtp/0/$value/proto"
    private fun wrapMls(value: String): String = "/xmtp/mls/1/$value/proto"

    companion object {
        /**
         * This method allows to know if the [Topic] is valid according to the accepted characters
         * @param topic String that represents the topic that will be evaluated
         * @return if the topic is valid
         */
        fun isValidTopic(topic: String): Boolean {
            val regex = Regex("^[\\x00-\\x7F]+$") // Use this regex to filter non ASCII chars
            val index = topic.indexOf("0/")
            if (index != -1) {
                val unwrappedTopic = topic.substring(index + 2, topic.lastIndexOf("/proto"))
                return unwrappedTopic.matches(regex)
            }
            return false
        }
    }
}
