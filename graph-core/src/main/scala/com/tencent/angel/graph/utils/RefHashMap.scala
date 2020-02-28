package com.tencent.angel.graph.utils

import com.tencent.angel.graph.VertexId
import it.unimi.dsi.fastutil.ints._
import it.unimi.dsi.fastutil.longs._

import scala.reflect._
import scala.reflect.runtime.universe._

class RefHashMap[V: ClassTag : TypeTag](global2local: FastHashMap[VertexId, Int],
                                        local2global: Array[VertexId],
                                        private val values: Array[V],
                                        private val bitSet: BitSet)
  extends FastHashMap[VertexId, V](null, null, false, 0, 0.75f, 0) {

  def this(global2local: FastHashMap[VertexId, Int], local2global: Array[VertexId], values: Array[V]) = {
    this(global2local, local2global, values, new BitSet(values.length))
  }

  def this(global2local: FastHashMap[VertexId, Int], local2global: Array[VertexId]) {
    this(global2local, local2global, new Array[V](local2global.length), new BitSet(local2global.length))
  }

  override def size(): Int = bitSet.cardinality()

  override def containsKey(k: VertexId): Boolean = {
    if (global2local.containsKey(k)) {
      bitSet.get(global2local(k))
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }
  }

  /** Get the value for a given key */
  override def apply(k: VertexId): V = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)
      if (bitSet.get(pos)) {
        values(pos)
      } else {
        throw new Exception(s"Cannot find key $k")
      }
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }
  }

  override def get(k: VertexId): V = apply(k)

  /** Get the value for a given key, or returns elseValue if it doesn't exist. */
  override def getOrElse(k: VertexId, elseValue: V): V = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)
      if (bitSet.get(pos)) values(pos) else elseValue
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }
  }

  /** Set the value for a key */
  override def update(k: VertexId, v: V): this.type = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)
      if (bitSet.get(pos)) {
        values(pos) = v
      } else {
        bitSet.set(pos)
        values(pos) = v
      }
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }

    this
  }

  override def put(k: VertexId, v: V): this.type = update(k, v)

  override def putAll(keyArr: Array[VertexId], valueArr: Array[V]): this.type = {
    keyArr.zip(valueArr).foreach { case (key, value) => update(key, value) }

    this
  }

  override def putMerge(k: VertexId, v: V, mergeF: (V, V) => V): this.type = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)
      if (bitSet.get(pos)) {
        values(pos) = mergeF(values(pos), v)
      } else {
        bitSet.set(pos)
        values(pos) = v
      }
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }

    this
  }

  override def changeValue(k: VertexId, defaultValue: => V, mergeValue: V => V): V = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)
      if (bitSet.get(pos)) {
        values(pos) = mergeValue(values(pos))
      } else {
        bitSet.set(pos)
        values(pos) = defaultValue
      }

      values(pos)
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }
  }

  override def remove(k: VertexId): V = {
    if (global2local.containsKey(k)) {
      val pos = global2local(k)

      if (bitSet.get(pos)) {
        val preValue = values(pos)
        values(pos) = defaultValue
        bitSet.unset(pos)

        preValue
      } else {
        throw new Exception(s"Cannot find key $k")
      }
    } else {
      throw new Exception("Cannot add a new key in RefHashMap!")
    }

  }

  override def clear(): this.type = {
    bitSet.clear()
    this
  }

  override def foreach(func: (VertexId, V) => Unit): Unit = {
    bitSet.iterator.foreach(pos => func(local2global(pos), values(pos)))
  }

  override def foreachKey(func: VertexId => Unit): Unit = {
    bitSet.iterator.foreach(pos => func(local2global(pos)))
  }

  override def foreachValue(func: V => Unit): Unit = {
    bitSet.iterator.foreach(pos => func(values(pos)))
  }

  override def iterator: Iterator[(VertexId, V)] = new Iterator[(VertexId, V)] {
    private val bitSetIter = bitSet.iterator

    def hasNext: Boolean = bitSetIter.hasNext

    def next(): (VertexId, V) = {
      val pos = bitSetIter.next()
      local2global(pos) -> values(pos)
    }
  }

  override def KeyIterator(): Iterator[VertexId] = {
    new Iterator[VertexId] {
      private val bitSetIter = bitSet.iterator

      def hasNext: Boolean = bitSetIter.hasNext

      def next(): VertexId = {
        val pos = bitSetIter.next()
        local2global(pos)
      }
    }
  }

  override def valueIterator(): Iterator[V] = {
    new Iterator[V] {
      private val bitSetIter = bitSet.iterator

      def hasNext: Boolean = bitSetIter.hasNext

      def next(): V = {
        val pos = bitSetIter.next()
        values(pos)
      }
    }
  }

  override def mapValues[U: ClassTag : TypeTag](func: V => U): RefHashMap[U] = {
    val newValues = new Array[U](values.length)
    val newBitSet = new BitSet(values.length)

    bitSet.iterator.foreach { pos =>
      newBitSet.set(pos)
      newValues(pos) = func(values(pos))
    }

    new RefHashMap[U](global2local, local2global, newValues, newBitSet)
  }

  override def asIdMaps: (FastHashMap[VertexId, Int], Array[VertexId]) = {
    global2local -> local2global
  }

  override def toUnimi[T]: T = {
    (keyTag, valueTag) match {
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Boolean] =>
        val temp = new Int2BooleanOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Boolean])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Char] =>
        val temp = new Int2CharOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Char])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Byte] =>
        val temp = new Int2ByteOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Byte])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Short] =>
        val temp = new Int2ShortOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Short])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Int] =>
        val temp = new Int2IntOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Int])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Long] =>
        val temp = new Int2LongOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Long])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Float] =>
        val temp = new Int2FloatOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Float])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Int] && vt == classOf[Double] =>
        val temp = new Int2DoubleOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos).asInstanceOf[Double])
        }
        temp.asInstanceOf[T]
      case (kt, _) if kt == classOf[Int] =>
        val temp = new Int2ObjectOpenHashMap[V]()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Int], values(pos))
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Boolean] =>
        val temp = new Long2BooleanOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Boolean])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Char] =>
        val temp = new Long2CharOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Char])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Byte] =>
        val temp = new Long2ByteOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Byte])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Short] =>
        val temp = new Long2ShortOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Short])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Int] =>
        val temp = new Long2IntOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Int])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Long] =>
        val temp = new Long2LongOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Long])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Float] =>
        val temp = new Long2FloatOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Float])
        }
        temp.asInstanceOf[T]
      case (kt, vt) if kt == classOf[Long] && vt == classOf[Double] =>
        val temp = new Long2DoubleOpenHashMap()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos).asInstanceOf[Double])
        }
        temp.asInstanceOf[T]
      case (kt, _) if kt == classOf[Long] =>
        val temp = new Long2ObjectOpenHashMap[V]()
        bitSet.iterator.foreach { pos =>
          temp.put(local2global(pos).asInstanceOf[Long], values(pos))
        }
        temp.asInstanceOf[T]
    }
  }
}