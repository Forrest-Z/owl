// code by jph
package ch.ethz.idsc.owl.math.region;

import java.io.Serializable;

public class RegionDifference<T> implements Region<T>, Serializable {
  public static <T> Region<T> of(Region<T> belongs, Region<T> butNot) {
    return new RegionDifference<>(belongs, butNot);
  }

  // ---
  private final Region<T> belongs;
  private final Region<T> butNot;

  private RegionDifference(Region<T> belongs, Region<T> butNot) {
    this.belongs = belongs;
    this.butNot = butNot;
  }

  @Override
  public final boolean isMember(T element) {
    return belongs.isMember(element) //
        && !butNot.isMember(element);
  }
}
