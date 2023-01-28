package org.xmtp.android.library.codecs


public typealias ContentTypeID = org.xmtp.proto.message.contents.Content.ContentTypeId

public constructor(ContentTypeID.authorityID: String, typeID: String, versionMajor: Int, versionMinor: Int) : this() {    this.authorityID = authorityID
    this.typeID = typeID
    this.versionMajor = UInt32(versionMajor)
    this.versionMinor = UInt32(versionMinor)
}
val ContentTypeID.id: String
    get() = "${authorityID}:${typeID}"