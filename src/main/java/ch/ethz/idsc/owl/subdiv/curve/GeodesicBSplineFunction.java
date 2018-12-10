// code by jph
package ch.ethz.idsc.owl.subdiv.curve;

import java.util.stream.IntStream;

import ch.ethz.idsc.owl.math.GeodesicInterface;
import ch.ethz.idsc.tensor.RationalScalar;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Range;
import ch.ethz.idsc.tensor.opt.ScalarTensorFunction;
import ch.ethz.idsc.tensor.sca.Clip;
import ch.ethz.idsc.tensor.sca.Floor;

/** The implementation of BSplineFunction in the tensor library
 * is different from Mathematica.
 * 
 * tensor::BSplineFunction is parameterized over the interval
 * [0, control.length()-1]
 * 
 * tensor::BSplineFunction can be instantiated for all degrees
 * regardless of the length of the control points.
 * 
 * Mathematica::BSplineFunction throws an exception if number
 * of control points is insufficient for the specified degree. */
public class GeodesicBSplineFunction implements ScalarTensorFunction {
  /** the control point are stored by reference, i.e. modifications to
   * given tensor alter the behavior of this BSplineFunction instance.
   * 
   * @param degree of polynomial basis function, non-negative integer
   * @param control points with at least one element
   * @return */
  public static GeodesicBSplineFunction of(GeodesicInterface geodesicInterface, int degree, Tensor control) {
    if (degree < 0)
      throw new IllegalArgumentException("" + degree);
    return new GeodesicBSplineFunction(geodesicInterface, degree, control);
  }

  // ---
  private final GeodesicInterface geodesicInterface;
  private final int degree;
  private final Tensor control;
  /** half == degree / 2 */
  private final int half;
  /** shift is 0 for odd degree and 1/2 for even degree */
  private final Scalar shift;
  /** index of last control point */
  private final int last;
  /** domain of this function */
  private final Clip domain;
  /** clip for knots */
  private final Clip clip;
  private final Tensor KNOTS;

  private GeodesicBSplineFunction(GeodesicInterface geodesicInterface, int degree, Tensor control) {
    this.geodesicInterface = geodesicInterface;
    this.degree = degree;
    this.control = control;
    half = degree / 2;
    shift = degree % 2 == 0 //
        ? RationalScalar.HALF
        : RealScalar.ZERO;
    last = control.length() - 1;
    domain = Clip.function(0, last);
    clip = Clip.function( //
        domain.min().add(shift), //
        domain.max().add(shift));
    KNOTS = Range.of(0, control.length());
  }

  /** @param scalar inside interval [0, control.length() - 1]
   * @return
   * @throws Exception if given scalar is outside required interval */
  @Override
  public Tensor apply(Scalar scalar) {
    scalar = domain.requireInside(scalar).add(shift);
    return deBoor(Floor.FUNCTION.apply(scalar).number().intValue()).apply(scalar);
  }

  /** @param k in the interval [0, control.length() - 1]
   * @return */
  public GeodesicDeBoor deBoor(int k) {
    int lo = -degree + 1 + k;
    int hi = +degree + 1 + k;
    Tensor knots1 = Range.of(lo, hi).map(clip);
    Tensor knots2 = Tensor.of(IntStream.range(lo, hi) //
        .map(i -> Math.min(Math.max(0, i), control.length() - 1)) //
        .mapToObj(KNOTS::get)) //
        .map(clip);
    // if (!knots1.equals(knots2)) {
    // System.out.println("---");
    // System.out.println("k=" + k + " [" + lo + ", " + hi + "]");
    // System.out.println(knots1);
    // System.out.println(knots2);
    // throw TensorRuntimeException.of(knots1, knots2);
    // }
    return new GeodesicDeBoor(geodesicInterface, degree, knots1, //
        Tensor.of(IntStream.range(k - half, hi - half) // control
            .map(this::bound) //
            .mapToObj(control::get)));
  }

  // helper function
  private int bound(int index) {
    return Math.min(Math.max(0, index), last);
  }
}
