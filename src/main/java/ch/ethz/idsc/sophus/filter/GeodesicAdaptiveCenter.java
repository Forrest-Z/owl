// code by ob / jph
package ch.ethz.idsc.sophus.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ch.ethz.idsc.sophus.math.GeodesicInterface;
import ch.ethz.idsc.sophus.math.IntegerTensorFunction;
import ch.ethz.idsc.sophus.math.WindowCenterSampler;
import ch.ethz.idsc.tensor.RationalScalar;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Scalars;
import ch.ethz.idsc.tensor.SymmetricVectorQ;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.TensorRuntimeException;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Reverse;
import ch.ethz.idsc.tensor.opt.TensorUnaryOperator;
import ch.ethz.idsc.tensor.red.Norm;
import ch.ethz.idsc.tensor.sca.ScalarUnaryOperator;

/** GeodesicCenter projects a sequence of points to their geodesic center
 * with each point weighted as provided by an external function.
 * 
 * <p>Careful: the implementation only supports sequences with ODD number of elements!
 * When a sequence of even length is provided an Exception is thrown. */
public class GeodesicAdaptiveCenter implements TensorUnaryOperator {
  private static final Scalar TWO = RealScalar.of(2);

  /** @param geodesicInterface
   * @param function that maps an extent to a weight mask of length == 2 * extent + 1
   * @return operator that maps a sequence of odd number of points to their geodesic center
   * @throws Exception if either input parameter is null */
  public static TensorUnaryOperator of(GeodesicInterface geodesicInterface, IntegerTensorFunction function, Scalar interval) {
    return new GeodesicAdaptiveCenter(geodesicInterface, Objects.requireNonNull(function), interval);
  }

  /** @param geodesicInterface
   * @param windowFunction
   * @return
   * @throws Exception if either input parameter is null */
  public static TensorUnaryOperator of(GeodesicInterface geodesicInterface, ScalarUnaryOperator windowFunction, Scalar interval) {
    return new GeodesicAdaptiveCenter(geodesicInterface, new WindowCenterSampler(windowFunction), interval);
  }

  // ---
  private final GeodesicInterface geodesicInterface;
  private final IntegerTensorFunction function;
  private final List<Tensor> weights = new ArrayList<>();
  private final Scalar interval;

  private GeodesicAdaptiveCenter(GeodesicInterface geodesicInterface, IntegerTensorFunction function, Scalar interval) {
    this.geodesicInterface = Objects.requireNonNull(geodesicInterface);
    this.function = function;
    this.interval = interval;
  }

  @Override // from TensorUnaryOperator
  public Tensor apply(Tensor tensor) {
    System.out.println(tensor);
    if (tensor.length() % 2 != 1)
      throw TensorRuntimeException.of(tensor);
    int radius = (tensor.length() - 1) / 2;
    // Downsizes Filter by removing all elements that are further away than the threshold from the center
    // Keeps filter symmetric
    while (Scalars.lessEquals(interval, Norm._2.between(tensor.get(0).extract(0, 2), tensor.get(radius).extract(0, 2)))
        || Scalars.lessEquals(interval, Norm._2.between(tensor.get(tensor.length() - 1).extract(0, 2), tensor.get(radius).extract(0, 2)))) {
      radius = radius - 1;
      tensor = tensor.extract(1, tensor.length() - 1);
    }
    synchronized (weights) {
      while (weights.size() <= radius)
        weights.add(splits(function.apply(weights.size())));
    }
    Tensor splits = weights.get(radius);
    Tensor pL = tensor.get(0);
    Tensor pR = tensor.get(2 * radius);
    for (int index = 0; index < radius;) {
      Scalar scalar = splits.Get(index++);
      pL = geodesicInterface.split(pL, tensor.get(index), scalar);
      pR = geodesicInterface.split(pR, tensor.get(2 * radius - index), scalar);
    }
    return geodesicInterface.split(pL, pR, RationalScalar.HALF);
  }

  /** @param mask symmetric vector of odd length
   * @return weights of Kalman-style iterative moving average
   * @throws Exception if mask is not symmetric or has even number of elements */
  /* package */ static Tensor splits(Tensor mask) {
    if (mask.length() % 2 == 0)
      throw TensorRuntimeException.of(mask);
    SymmetricVectorQ.require(mask);
    int radius = (mask.length() - 1) / 2;
    Tensor halfmask = Tensors.vector(i -> i == 0 //
        ? mask.Get(radius + i)
        : mask.Get(radius + i).multiply(TWO), radius);
    Scalar factor = RealScalar.ONE;
    Tensor splits = Tensors.empty();
    for (int index = 0; index < radius; ++index) {
      Scalar lambda = halfmask.Get(index).divide(factor);
      splits.append(lambda);
      factor = factor.multiply(RealScalar.ONE.subtract(lambda));
    }
    return Reverse.of(splits);
  }
}
