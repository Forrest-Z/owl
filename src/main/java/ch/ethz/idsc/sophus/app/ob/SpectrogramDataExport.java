// code by ob
package ch.ethz.idsc.sophus.app.ob;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import ch.ethz.idsc.sophus.filter.GeodesicCenter;
import ch.ethz.idsc.sophus.filter.GeodesicCenterFilter;
import ch.ethz.idsc.sophus.group.LieDifferences;
import ch.ethz.idsc.sophus.group.LieGroup;
import ch.ethz.idsc.sophus.group.Se2CoveringExponential;
import ch.ethz.idsc.sophus.group.Se2Geodesic;
import ch.ethz.idsc.sophus.group.Se2Group;
import ch.ethz.idsc.sophus.math.SmoothingKernel;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.io.Export;
import ch.ethz.idsc.tensor.io.ResourceData;
import ch.ethz.idsc.tensor.opt.TensorUnaryOperator;

/* package */ class SpectrogramDataExport {
  protected static Tensor _control = null;
  public static final File ROOT = new File("C:/Users/Oliver/Desktop/MA/owl_export");

  private static void process() throws IOException {
    List<String> dataSource = ResourceData.lines("/dubilab/app/pose/index.vector");
    List<SmoothingKernel> kernel = Arrays.asList(SmoothingKernel.GAUSSIAN, SmoothingKernel.HAMMING, SmoothingKernel.BLACKMAN);
    // iterate over data
    for (String data : dataSource) {
      // iterate over Kernels
      // load data
      _control = Tensor.of(ResourceData.of("/dubilab/app/pose/" + data + ".csv").stream().map(row -> row.extract(1, 4)));
      for (SmoothingKernel smoothingKernel : kernel) {
        // iterate over radius
        // Create Geod. Center instance
        TensorUnaryOperator tensorUnaryOperator = GeodesicCenter.of(Se2Geodesic.INSTANCE, smoothingKernel);
        for (int radius = 0; radius < 15; radius++) {
          // Create new Geod. Center
          Tensor refined = GeodesicCenterFilter.of(tensorUnaryOperator, radius).apply(_control);
          System.out.println(data + smoothingKernel.toString() + radius);
          System.err.println(speeds(refined));
          // export velocities
          Export.of(new File(ROOT, "190319/" + data.replace('/', '_') + "_" + smoothingKernel.toString() + "_" + radius + ".csv"), refined);
        }
      }
    }
  }

  private static Tensor speeds(Tensor refined) {
    LieGroup lieGroup = Se2Group.INSTANCE;
    Tensor speeds = Tensors.empty();
    if (Objects.nonNull(lieGroup)) {
      LieDifferences lieDifferences = new LieDifferences(lieGroup, Se2CoveringExponential.INSTANCE);
      Scalar SAMPLING_FREQUENCY = RealScalar.of(20.0);
      speeds = lieDifferences.apply(refined).multiply(SAMPLING_FREQUENCY);
    }
    return speeds;
  }

  public static void main(String[] args) throws IOException {
    process();
  }
}