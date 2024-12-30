// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.syntax.all._

/**
 * Cache based on a two-generation GC.
 * Taken from https://twitter.com/pchiusano/status/1260255494519865346
 */
sealed abstract case class SemispaceCache[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int) {

  private val emptyMap: Map[K, V] = Map.empty

  assert(max >= 0)
  assert(gen0.size <= max)
  assert(gen1.size <= max)

  def insert(k: K, v: V): (SemispaceCache[K, V], Map[K, V]) =
    if (max == 0)             (this, Map(k -> v))                                       // special case, can't insert!
    else if (gen0.size < max) (SemispaceCache(gen0 + (k -> v), gen1, max), emptyMap) // room in gen0, done!
    else                      (SemispaceCache(Map(k -> v), gen0, max), gen1)     // no room in gen0, slide it down

  def lookup(k: K): Option[(SemispaceCache[K, V], V, Map[K, V])] =
    gen0.get(k).tupleLeft(this).map { case (c, v) => (c, v, emptyMap)} orElse       // key is in gen0, done!
    gen1.get(k).map { v => 
      val (c, m) =  insert(k, v)
      (c, v, m)
    }  // key is in gen1, copy to gen0

  def containsKey(k: K): Boolean =
    gen0.contains(k) || gen1.contains(k)

  def values: List[V] =
    (gen0.values.toSet | gen1.values.toSet).toList
}

object SemispaceCache {

  private def apply[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int): SemispaceCache[K, V] =
    new SemispaceCache[K, V](gen0, gen1, max) {}

  def empty[K, V](max: Int): SemispaceCache[K, V] =
    SemispaceCache[K, V](Map.empty, Map.empty, max max 0)

}
