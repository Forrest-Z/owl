// code by jph
package ch.ethz.idsc.sophus.app.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

import ch.ethz.idsc.owl.gui.GraphicsUtil;
import ch.ethz.idsc.owl.gui.win.GeometricLayer;
import ch.ethz.idsc.owl.math.map.Se2Utils;
import ch.ethz.idsc.owl.math.planar.Arrowhead;
import ch.ethz.idsc.sophus.curve.GeodesicBSplineFunction;
import ch.ethz.idsc.sophus.curve.LieGroupBSplineInterpolation;
import ch.ethz.idsc.sophus.group.RnGeodesic;
import ch.ethz.idsc.sophus.group.RnGroup;
import ch.ethz.idsc.sophus.group.Se2CoveringGeodesic;
import ch.ethz.idsc.sophus.group.Se2CoveringGroup;
import ch.ethz.idsc.sophus.symlink.SymLinkImage;
import ch.ethz.idsc.sophus.symlink.SymLinkImages;
import ch.ethz.idsc.tensor.RationalScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.alg.Subdivide;

/* package */ class GeodesicBSplineFunctionDemo extends ControlPointsDemo {
  private static final List<Integer> DEGREES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  private static final Tensor ARROWHEAD_LO = Arrowhead.of(0.18);
  // ---
  private final SpinnerLabel<Integer> spinnerDegree = new SpinnerLabel<>();
  private final SpinnerLabel<Integer> spinnerRefine = new SpinnerLabel<>();
  private final JToggleButton jToggleCtrl = new JToggleButton("ctrl");
  private final JToggleButton jToggleComb = new JToggleButton("comb");
  private final JToggleButton jToggleItrp = new JToggleButton("interp");
  private final JToggleButton jToggleLine = new JToggleButton("line");
  private final JToggleButton jToggleSymi = new JToggleButton("graph");
  private final JSlider jSlider = new JSlider(0, 1000, 500);

  GeodesicBSplineFunctionDemo() {
    {
      JButton jButton = new JButton("clear");
      jButton.addActionListener(actionEvent -> setControl(Tensors.of(Array.zeros(3), Tensors.vector(1, 0, 0))));
      timerFrame.jToolBar.add(jButton);
    }
    jToggleCtrl.setSelected(true);
    timerFrame.jToolBar.add(jToggleCtrl);
    // ---
    jToggleComb.setSelected(true);
    timerFrame.jToolBar.add(jToggleComb);
    // ---
    jToggleLine.setSelected(false);
    timerFrame.jToolBar.add(jToggleLine);
    // ---
    timerFrame.jToolBar.addSeparator();
    addButtonDubins();
    // ---
    timerFrame.jToolBar.add(jToggleItrp);
    // ---
    timerFrame.jToolBar.add(jToggleButton);
    jToggleSymi.setSelected(true);
    timerFrame.jToolBar.add(jToggleSymi);
    // ---
    jSlider.setPreferredSize(new Dimension(500, 28));
    timerFrame.jToolBar.add(jSlider);
    // ---
    spinnerDegree.setList(DEGREES);
    spinnerDegree.setValue(3);
    spinnerDegree.addToComponentReduced(timerFrame.jToolBar, new Dimension(50, 28), "degree");
    // ---
    spinnerRefine.setList(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    spinnerRefine.setValue(5);
    spinnerRefine.addToComponentReduced(timerFrame.jToolBar, new Dimension(50, 28), "refinement");
    {
      Tensor blub = Tensors.fromString("{{1,0,0},{1,0,0},{2,0,2.5708},{1,0,2.1},{1.5,0,0},{2.3,0,-1.2},{1.5,0,0},{4,0,3.14159},{2,0,3.14159},{2,0,0}}");
      setControl(DubinsGenerator.of(Tensors.vector(0, 0, 2.1), //
          Tensor.of(blub.stream().map(row -> row.pmul(Tensors.vector(2, 1, 1))))));
    }
  }

  @Override // from RenderInterface
  public void render(GeometricLayer geometricLayer, Graphics2D graphics) {
    GraphicsUtil.setQualityHigh(graphics);
    final boolean isR2 = jToggleButton.isSelected();
    final Tensor control = controlSe2();
    final int degree = spinnerDegree.getValue();
    final int levels = spinnerRefine.getValue();
    int upper = control.length() - 1;
    final Tensor domain = Subdivide.of(0, upper, upper * (1 << (levels)));
    final Tensor refined;
    final int value = jSlider.getValue();
    final Scalar parameter = RationalScalar.of(value * upper, 1000);
    if (jToggleSymi.isSelected()) {
      SymLinkImage symLinkImage = SymLinkImages.deBoor(degree, upper + 1, parameter);
      graphics.drawImage(symLinkImage.bufferedImage(), 0, 0, null);
    }
    renderControlPoints(geometricLayer, graphics);
    GeodesicBSplineFunction geodesicBSplineFunction;
    if (isR2) {
      Tensor rnctrl = controlR2();
      Tensor effective = jToggleItrp.isSelected() //
          ? new LieGroupBSplineInterpolation(RnGroup.INSTANCE, RnGeodesic.INSTANCE, degree, rnctrl).apply()
          : rnctrl;
      geodesicBSplineFunction = //
          GeodesicBSplineFunction.of(RnGeodesic.INSTANCE, degree, effective);
      refined = domain.map(geodesicBSplineFunction);
      {
        graphics.setColor(new Color(0, 0, 255, 128));
        graphics.draw(geometricLayer.toPath2D(refined));
      }
    } else { // SE2
      Tensor effective = jToggleItrp.isSelected() //
          ? new LieGroupBSplineInterpolation(Se2CoveringGroup.INSTANCE, Se2CoveringGeodesic.INSTANCE, degree, control).apply()
          : control;
      geodesicBSplineFunction = //
          GeodesicBSplineFunction.of(Se2CoveringGeodesic.INSTANCE, degree, effective);
      refined = domain.map(geodesicBSplineFunction);
      {
        Tensor selected = geodesicBSplineFunction.apply(parameter);
        geometricLayer.pushMatrix(Se2Utils.toSE2Matrix(selected));
        Path2D path2d = geometricLayer.toPath2D(ARROWHEAD_HI);
        graphics.setColor(Color.DARK_GRAY);
        graphics.fill(path2d);
        geometricLayer.popMatrix();
      }
    }
    new CurveRender(refined, false, jToggleComb.isSelected()).render(geometricLayer, graphics);
    if (!isR2 && levels < 5)
      for (Tensor point : refined) {
        geometricLayer.pushMatrix(Se2Utils.toSE2Matrix(point));
        Path2D path2d = geometricLayer.toPath2D(ARROWHEAD_LO);
        geometricLayer.popMatrix();
        int rgb = 128 + 32;
        path2d.closePath();
        graphics.setColor(new Color(rgb, rgb, rgb, 128 + 64));
        graphics.fill(path2d);
        graphics.setColor(Color.BLACK);
        graphics.draw(path2d);
      }
  }

  public static void main(String[] args) {
    AbstractDemo abstractDemo = new GeodesicBSplineFunctionDemo();
    abstractDemo.timerFrame.jFrame.setBounds(100, 100, 1000, 600);
    abstractDemo.timerFrame.jFrame.setVisible(true);
  }
}