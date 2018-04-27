package helm

/**
  * Representative of a response from Consul for an API call querying data, including
  * the values of the headers X-Consul-Index, X-Consul-Knownleader, and X-Consul-Lastcontact
  * Note that the following APIs are known not to return these headers (as of Consul v1.0.6):
  *   - Anything under /agent/
  *   - /catalog/datacenters
  */
final case class QueryResponse[A](
  value:       A,
  index:       Long,
  knownLeader: Boolean,
  lastContact: Long
)
