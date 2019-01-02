// code by jph
package ch.ethz.idsc.sophus.app.curve;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import ch.ethz.idsc.owl.gui.GraphicsUtil;
import ch.ethz.idsc.owl.gui.win.GeometricLayer;
import ch.ethz.idsc.owl.math.map.Se2Utils;
import ch.ethz.idsc.owl.math.planar.Arrowhead;
import ch.ethz.idsc.owl.math.planar.Extract2D;
import ch.ethz.idsc.owl.math.planar.SignedCurvature2D;
import ch.ethz.idsc.sophus.app.api.AbstractDemo;
import ch.ethz.idsc.sophus.app.api.ControlPointsDemo;
import ch.ethz.idsc.sophus.app.api.GeodesicDisplay;
import ch.ethz.idsc.sophus.app.api.GeodesicDisplays;
import ch.ethz.idsc.sophus.app.util.CurveRender;
import ch.ethz.idsc.sophus.app.util.DubinsGenerator;
import ch.ethz.idsc.sophus.app.util.SpinnerLabel;
import ch.ethz.idsc.sophus.curve.BSpline1CurveSubdivision;
import ch.ethz.idsc.sophus.curve.BSpline4CurveSubdivision;
import ch.ethz.idsc.sophus.curve.CurveSubdivision;
import ch.ethz.idsc.sophus.group.Se2CoveringGeodesic;
import ch.ethz.idsc.sophus.math.GeodesicInterface;
import ch.ethz.idsc.tensor.RationalScalar;
import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Differences;
import ch.ethz.idsc.tensor.alg.Join;
import ch.ethz.idsc.tensor.alg.Range;
import ch.ethz.idsc.tensor.alg.Transpose;
import ch.ethz.idsc.tensor.img.ColorDataIndexed;
import ch.ethz.idsc.tensor.img.ColorDataLists;
import ch.ethz.idsc.tensor.mat.Inverse;
import ch.ethz.idsc.tensor.opt.BSplineInterpolation;
import ch.ethz.idsc.tensor.opt.TensorUnaryOperator;
import ch.ethz.idsc.tensor.red.Nest;
import ch.ethz.idsc.tensor.red.Norm;
import ch.ethz.idsc.tensor.sca.InvertUnlessZero;

/* package */ class CurveSubdivisionDemo extends ControlPointsDemo {
  private static final boolean BSPLINE4 = false;
  private static final Tensor ARROWHEAD_LO = Arrowhead.of(0.18);
  private static final ColorDataIndexed COLOR_DATA_INDEXED = //
      ColorDataLists._097.cyclic().deriveWithAlpha(128 + 64);
  // private static final Tensor DUBILAB = //
  // ResourceData.of("/dubilab/controlpoints/eight/20180603.csv").multiply(RealScalar.of(.4)).unmodifiable();
  // ---
  private final SpinnerLabel<CurveSubdivisionSchemes> spinnerLabel = new SpinnerLabel<>();
  private final SpinnerLabel<Integer> spinnerRefine = new SpinnerLabel<>();
  private final SpinnerLabel<Scalar> spinnerMagicC = new SpinnerLabel<>();
  private final JToggleButton jToggleCtrl = new JToggleButton("ctrl");
  private final JToggleButton jToggleBndy = new JToggleButton("bndy");
  private final JToggleButton jToggleComb = new JToggleButton("comb");
  private final JToggleButton jToggleCrvt = new JToggleButton("crvt");
  private final JToggleButton jToggleLine = new JToggleButton("line");
  private final JToggleButton jToggleItrp = new JToggleButton("interp");
  private final JToggleButton jToggleCyclic = new JToggleButton("cyclic");
  // ---
  private boolean printref = false;

  CurveSubdivisionDemo() {
    super(true, GeodesicDisplays.ALL);
    Tensor control = null;
    {
      Tensor move = Tensors.fromString( //
          "{{1,0,0},{1,0,0},{2,0,2.5708},{1,0,2.1},{1.5,0,0},{2.3,0,-1.2},{1.5,0,0},{4,0,3.14159},{2,0,3.14159},{2,0,0}}");
      move = Tensor.of(move.stream().map(row -> row.pmul(Tensors.vector(2, 1, 1))));
      Tensor init = Tensors.vector(0, 0, 2.1);
      control = DubinsGenerator.of(init, move);
    }
    setControl(control);
    JTextField jTextField = new JTextField(10);
    jTextField.setPreferredSize(new Dimension(100, 28));
    {
      timerFrame.jToolBar.add(jTextField);
    }
    // {
    // JButton jButton = new JButton("print");
    // jButton.addActionListener(actionEvent -> {
    // System.out.println(control);
    // // long now = System.currentTimeMillis();
    // File file = UserHome.file("" + jTextField.getText() + ".csv");
    // // File file = new File("src/main/resources/subdiv/se2", now + ".csv");
    // try {
    // Export.of(file, control.map(CsvFormat.strict()));
    // } catch (Exception exception) {
    // exception.printStackTrace();
    // }
    // });
    // timerFrame.jToolBar.add(jButton);
    // }
    {
      JButton jButton = new JButton("p-ref");
      jButton.addActionListener(actionEvent -> printref = true);
      timerFrame.jToolBar.add(jButton);
    }
    jToggleCtrl.setSelected(true);
    timerFrame.jToolBar.add(jToggleCtrl);
    // ---
    jToggleBndy.setSelected(true);
    timerFrame.jToolBar.add(jToggleBndy);
    // ---
    jToggleComb.setSelected(true);
    timerFrame.jToolBar.add(jToggleComb);
    // ---
    jToggleCrvt.setSelected(false);
    timerFrame.jToolBar.add(jToggleCrvt);
    // ---
    jToggleLine.setSelected(false);
    timerFrame.jToolBar.add(jToggleLine);
    // ---
    timerFrame.jToolBar.addSeparator();
    addButtonDubins();
    // ---
    jToggleItrp.setSelected(false);
    timerFrame.jToolBar.add(jToggleItrp);
    // ---
    timerFrame.jToolBar.add(jToggleCyclic);
    // ---
    spinnerLabel.setArray(CurveSubdivisionSchemes.values());
    spinnerLabel.setIndex(2);
    spinnerLabel.addToComponentReduced(timerFrame.jToolBar, new Dimension(150, 28), "scheme");
    // ---
    spinnerRefine.setList(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    spinnerRefine.setValue(6);
    spinnerRefine.addToComponentReduced(timerFrame.jToolBar, new Dimension(50, 28), "refinement");
    // ---
    spinnerMagicC.addSpinnerListener(value -> CurveSubdivisionHelper.MAGIC_C = value);
    spinnerMagicC.setList( //
        Tensors.fromString("{1/100, 1/10, 1/8, 1/6, 1/4, 1/3, 1/2, 2/3, 9/10, 99/100}").stream() //
            .map(Scalar.class::cast) //
            .collect(Collectors.toList()));
    spinnerMagicC.setValue(RationalScalar.HALF);
    spinnerMagicC.addToComponentReduced(timerFrame.jToolBar, new Dimension(50, 28), "refinement");
    // ---
    {
      JSlider jSlider = new JSlider(1, 999, 500);
      jSlider.setPreferredSize(new Dimension(500, 28));
      jSlider.addChangeListener(changeEvent -> //
      CurveSubdivisionHelper.MAGIC_C = RationalScalar.of(jSlider.getValue(), 1000));
      timerFrame.jToolBar.add(jSlider);
    }
  }

  @Override
  public void render(GeometricLayer geometricLayer, Graphics2D graphics) {
    GraphicsUtil.setQualityHigh(graphics);
    final CurveSubdivisionSchemes scheme = spinnerLabel.getValue();
    Function<GeodesicInterface, CurveSubdivision> function = spinnerLabel.getValue().function;
    GeodesicDisplay geodesicDisplay = geodesicDisplay();
    boolean isCyclic = jToggleCyclic.isSelected();
    Tensor control = control();
    if (jToggleBndy.isSelected() && !isCyclic && 1 < control.length()) {
      switch (scheme) {
      case BSPLINE2:
      case BSPLINE4:
      case BSPLINE4S3:
      case BSPLINE4S2:
        control = Join.of( //
            control.extract(0, 1), //
            control, //
            control.extract(control.length() - 1, control.length()));
        break;
      default:
        break;
      }
    }
    int levels = spinnerRefine.getValue();
    final Tensor refined;
    renderControlPoints(geometricLayer, graphics);
    {
      CurveSubdivision curveSubdivision = function.apply(geodesicDisplay.geodesicInterface());
      // Tensor rnctrl = control();
      TensorUnaryOperator tuo = jToggleCyclic.isSelected() //
          ? curveSubdivision::cyclic
          : curveSubdivision::string;
      if (jToggleItrp.isSelected() && scheme.degree.isPresent())
        control = BSplineInterpolation.solve(scheme.degree.get(), control);
      // ---
      refined = Nest.of(tuo, control, levels);
      // se2ctrl = new LieGroupBSplineInterpolation( //
      // Se2CoveringGroup.INSTANCE, Se2CoveringGeodesic.INSTANCE, scheme.degree.get(), se2ctrl).apply();
      // refined = Nest.of(subdivision, se2ctrl, levels);
      {
        graphics.setColor(new Color(0, 0, 255, 128));
        graphics.draw(geometricLayer.toPath2D(refined));
      }
    }
    // else { // SE2
    // CurveSubdivision curveSubdivision = function.apply(Se2CoveringGeodesic.INSTANCE);
    // TensorUnaryOperator subdivision = isCyclic //
    // ? curveSubdivision::cyclic
    // : curveSubdivision::string;
    // Tensor se2ctrl = _control.copy();
    // if (jToggleItrp.isSelected() && scheme.degree.isPresent())
    // se2ctrl = new LieGroupBSplineInterpolation( //
    // Se2CoveringGroup.INSTANCE, Se2CoveringGeodesic.INSTANCE, scheme.degree.get(), se2ctrl).apply();
    // refined = Nest.of(subdivision, se2ctrl, levels);
    // }
    if (jToggleLine.isSelected()) {
      CurveSubdivision curveSubdivision = new BSpline1CurveSubdivision(geodesicDisplay.geodesicInterface());
      TensorUnaryOperator tuo = isCyclic //
          ? curveSubdivision::cyclic
          : curveSubdivision::string;
      Tensor linear = Nest.of(tuo, control, 8);
      graphics.setColor(new Color(0, 255, 0, 128));
      Path2D path2d = geometricLayer.toPath2D(linear);
      if (isCyclic)
        path2d.closePath();
      // graphics.setStroke(new BasicStroke(1.25f));
      graphics.draw(path2d);
      // graphics.setStroke(new BasicStroke(1f));
    }
    {
      if (BSPLINE4) {
        CurveSubdivision curveSubdivision = BSpline4CurveSubdivision.of(Se2CoveringGeodesic.INSTANCE);
        // curveSubdivision.string(_control);
        Tensor refined2 = Nest.of(curveSubdivision::string, control, levels);
        graphics.setColor(Color.GREEN);
        Path2D path2d = geometricLayer.toPath2D(refined2);
        if (isCyclic)
          path2d.closePath();
        graphics.setStroke(new BasicStroke(1.25f));
        graphics.draw(path2d);
        graphics.setStroke(new BasicStroke(1f));
      }
    }
    new CurveRender(refined, isCyclic, jToggleComb.isSelected()).render(geometricLayer, graphics);
    if (jToggleCrvt.isSelected()) {
      graphics.setStroke(new BasicStroke(1.25f));
      Tensor matrix = geometricLayer.getMatrix();
      geometricLayer.pushMatrix(Inverse.of(matrix));
      geometricLayer.pushMatrix(Tensors.fromString("{{1,0,0},{0,-50,100},{0,0,1}}"));
      Tensor points = Tensor.of(refined.stream().map(Extract2D::of));
      {
        graphics.setColor(COLOR_DATA_INDEXED.getColor(0));
        Tensor curvature = SignedCurvature2D.string(points);
        Tensor domain = Range.of(0, curvature.length());
        graphics.draw(geometricLayer.toPath2D(Transpose.of(Tensors.of(domain, curvature))));
      }
      Tensor diffs = Differences.of(refined.get(Tensor.ALL, 2));
      {
        graphics.setColor(COLOR_DATA_INDEXED.getColor(1));
        Tensor domain = Range.of(0, diffs.length());
        graphics.draw(geometricLayer.toPath2D(Transpose.of(Tensors.of(domain, diffs))));
      }
      Tensor arclen = Tensor.of(Differences.of(points).stream().map(Norm._2::ofVector));
      {
        graphics.setColor(COLOR_DATA_INDEXED.getColor(2));
        Tensor domain = Range.of(0, arclen.length());
        graphics.draw(geometricLayer.toPath2D(Transpose.of(Tensors.of(domain, arclen))));
      }
      {
        graphics.setColor(COLOR_DATA_INDEXED.getColor(3));
        Tensor div = diffs.pmul(arclen.map(InvertUnlessZero.FUNCTION));
        Tensor domain = Range.of(0, div.length());
        graphics.draw(geometricLayer.toPath2D(Transpose.of(Tensors.of(domain, div.multiply(RealScalar.of(-1))))));
      }
      geometricLayer.popMatrix();
      geometricLayer.popMatrix();
      graphics.setStroke(new BasicStroke(1f));
    }
    if (levels < 5)
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
    if (printref) {
      printref = false;
      System.out.println(refined);
    }
  }

  public static void main(String[] args) {
    AbstractDemo abstractDemo = new CurveSubdivisionDemo();
    abstractDemo.timerFrame.jFrame.setBounds(100, 100, 1000, 600);
    abstractDemo.timerFrame.jFrame.setVisible(true);
  }
}