// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.{ Functor, ~> }
import cats.syntax.all._
import skunk.Statement
import cats.effect.kernel.Ref
import skunk.data.SemispaceCache

/** An LRU (by access) cache, keyed by statement `CacheKey`. */
sealed trait StatementCache[F[_], V] { outer =>

  /**
    * @return entry along with any values evicted as a result of the retrieval
    */
  def get(k: Statement[_]): F[Option[(V, List[V])]]
  private[skunk] def put(k: Statement[_], v: V): F[List[V]]
  def containsKey(k: Statement[_]): F[Boolean]
  def clear: F[Unit]
  def values: F[List[V]]

  def mapK[G[_]](fk: F ~> G): StatementCache[G, V] =
    new StatementCache[G, V] {
      def get(k: Statement[_]): G[Option[(V, List[V])]] = fk(outer.get(k))
      def put(k: Statement[_], v: V): G[List[V]] = fk(outer.put(k, v))
      def containsKey(k: Statement[_]): G[Boolean] = fk(outer.containsKey(k))
      def clear: G[Unit] = fk(outer.clear)
      def values: G[List[V]] = fk(outer.values)
    }

}

object StatementCache {

  def empty[F[_]: Functor: Ref.Make, V](max: Int): F[StatementCache[F, V]] =
    Ref[F].of(SemispaceCache.empty[Statement.CacheKey, V](max)).map { ref =>
      new StatementCache[F, V] {

        def get(k: Statement[_]): F[Option[(V, List[V])]] =
          ref.modify { c =>
            c.lookup(k.cacheKey) match {
              case Some((cʹ, v, evicted)) => 
                (cʹ, Some((v, evicted.values.toList)))
              case None          => 
                (c, None)
            }
          }

        private[skunk] def put(k: Statement[_], v: V): F[List[V]] =
          ref.modify { c =>
            c.insert(k.cacheKey, v) match {
              case (cache, evictedMap) =>
                (cache, evictedMap.values.toList)

            }
          }


        def containsKey(k: Statement[_]): F[Boolean] =
          ref.get.map(_.containsKey(k.cacheKey))

        def clear: F[Unit] =
          ref.set(SemispaceCache.empty[Statement.CacheKey, V](max))

        def values: F[List[V]] =
          ref.get.map(_.values)
      }
    }

}
