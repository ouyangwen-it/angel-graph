package com.tencent.angel.graph.core.psf.get

import com.tencent.angel.graph.utils.{GUtils, ReflectUtils, SerDe}
import com.tencent.angel.ml.matrix.psf.get.base.{GetResult, PartitionGetResult}
import io.netty.buffer.ByteBuf
import com.tencent.angel.ml.math2.vector._

import scala.reflect.runtime.universe._

case class GGetResult(value: Any) extends GetResult

class GPartitionGetResult(var tt: TypeTag[_], var pResult: Any, var mergeFuncId: Int) extends PartitionGetResult {

  def this() = this(null, null, -1)

  override def serialize(byteBuf: ByteBuf): Unit = {
    SerDe.javaSerialize(tt, byteBuf)

    val tpe = tt.tpe
    tpe match {
      case t if GUtils.isPrimitive(t) => SerDe.serPrimitive(pResult, byteBuf)
      case t if GUtils.isPrimitiveArray(t) => SerDe.serArr(pResult, byteBuf)
      case t if GUtils.isFastMap(t) => SerDe.serFastMap(pResult, byteBuf)
      case t if GUtils.isVector(t) => SerDe.serVector(pResult.asInstanceOf[Vector], byteBuf)
      case _ => SerDe.serialize(pResult, ReflectUtils.getFields(tpe), byteBuf)
    }

    byteBuf.writeInt(mergeFuncId)
  }

  override def deserialize(byteBuf: ByteBuf): Unit = {
    val tt = SerDe.javaDeserialize[TypeTag[_]](byteBuf)
    val tpe = tt.tpe

    tpe match {
      case t if GUtils.isPrimitive(t) =>
        pResult = SerDe.primitiveFromBuffer(t, byteBuf)
      case t if GUtils.isPrimitiveArray(t) =>
        pResult = SerDe.arrFromBuffer(t.typeArgs.head, byteBuf)
      case t if GUtils.isFastMap(t) =>
        pResult = SerDe.fastMapFromBuffer(t, byteBuf)
      case t if GUtils.isVector(t) =>
        pResult = SerDe.vectorFromBuffer(t, byteBuf)
      case t =>
        pResult = ReflectUtils.newInstance(t)
        SerDe.deserialize(pResult, ReflectUtils.getFields(t), byteBuf)
    }

    mergeFuncId = byteBuf.readInt()
  }

  override def bufferLen(): Int = {
    val tpeLen = SerDe.javaSerBufferSize(tt)
    val tpe = tt.tpe

    val dataLen = tpe match {
      case t if GUtils.isPrimitive(t) => SerDe.serPrimitiveBufSize(pResult)
      case t if GUtils.isPrimitiveArray(t) => SerDe.serArrBufSize(t.typeArgs.head, pResult)
      case t if GUtils.isFastMap(t) => SerDe.serFastMapBufSize(pResult)
      case t if GUtils.isVector(t) => SerDe.serVectorBufSize(pResult.asInstanceOf[Vector])
      case t => SerDe.bufferLen(pResult, ReflectUtils.getFields(t))
    }

    tpeLen + dataLen + 4
  }
}