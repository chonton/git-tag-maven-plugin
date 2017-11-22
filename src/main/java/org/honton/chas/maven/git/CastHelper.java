package org.honton.chas.maven.git;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Cast a Serializable instance across ClassLoaders
 */
public class CastHelper {

  public static <T extends Serializable> T cast(Class<T> destClass, Object other)
    throws IOException, ClassNotFoundException {
    try {
      return destClass.cast(other);
    }
    catch (ClassCastException cce) {
      return (T)fromBytes(toBytes(other));
    }
  }

  private static byte[] toBytes(Object other) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      try (ObjectOutput out = new ObjectOutputStream(bos)) {
        out.writeObject(other);
      }
      return bos.toByteArray();
    }
  }

  private static Object fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
      try (ObjectInput in = new ObjectInputStream(bis)) {
        return in.readObject();
      }
    }
  }
}
