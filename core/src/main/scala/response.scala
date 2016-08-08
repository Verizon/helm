package helm

// there are other fields like CreateIndex, Flags, Session, etc but currently we don't care about them
private[helm] final case class KvResponse(value: String) extends AnyVal
private[helm] final case class KvResponses(values: List[KvResponse]) extends AnyVal
