// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.*;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix.sensors.PigeonIMU.CalibrationMode;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.pathplanner.lib.auto.AutoBuilder;

import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Direction;
import frc.robot.generated.TunerConstants;
import frc.robot.subsystems.Arm;
import frc.robot.subsystems.Climber;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.Elevator;
import frc.robot.subsystems.Intake;

import static edu.wpi.first.wpilibj2.command.Commands.none;
import static edu.wpi.first.wpilibj2.command.Commands.parallel;
import static edu.wpi.first.wpilibj2.command.Commands.run;
import static edu.wpi.first.wpilibj2.command.Commands.runOnce;
import static edu.wpi.first.wpilibj2.command.Commands.sequence;
import static edu.wpi.first.wpilibj2.command.Commands.waitSeconds;
import static edu.wpi.first.wpilibj2.command.Commands.waitUntil;

import java.util.function.BooleanSupplier;

public class RobotContainer {
    private double MaxSpeed = TunerConstants.kSpeedAt12Volts.in(MetersPerSecond); // kSpeedAt12Volts desired top speed
    private double MaxAngularRate = RotationsPerSecond.of(0.75).in(RadiansPerSecond); // 3/4 of a rotation per second max angular velocity

    /* Setting up bindings for necessary control of the swerve drive platform */
    private final SwerveRequest.FieldCentric drive = new SwerveRequest.FieldCentric()
            .withDeadband(MaxSpeed * 0.1).withRotationalDeadband(MaxAngularRate * 0.1) // Add a 10% deadband
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // Use open-loop control for drive motors
    //private final SwerveRequest.SwerveDriveBrake brake = new SwerveRequest.SwerveDriveBrake();
    //private final SwerveRequest.PointWheelsAt point = new SwerveRequest.PointWheelsAt();

    private final Telemetry logger = new Telemetry(MaxSpeed);

    public CommandXboxController joystick = new CommandXboxController(0);
    private final CommandXboxController coJoystick = new CommandXboxController(1);

    public final CommandSwerveDrivetrain drivetrain = TunerConstants.createDrivetrain();
    public final Elevator elevator = new Elevator();
    public final static Intake intake = new Intake();
    public final Arm arm = new Arm();
    public final Climber climber = new Climber();
    public final autoAlign autoAlign = new autoAlign(drivetrain, joystick,5);
    public SendableChooser<Boolean> mode = new SendableChooser<Boolean>();
    private final SendableChooser<Command> autoChooser;

    private SlewRateLimiter filter = new SlewRateLimiter(0.75);
   

    public RobotContainer() {

      mode.setDefaultOption("Competition", false);
      mode.addOption("Calibrate", true);

      SmartDashboard.putData("Mode", mode);

      if(mode.getSelected() == null){
        System.out.println("NO MODE");
      }
        
      autoChooser = AutoBuilder.buildAutoChooser("Test Auto");
      SmartDashboard.putData("Auto Mode", autoChooser);

           
    }

    public void configureBindings() {
      if(mode.getSelected() == null){
        System.out.println("NO MODE");
      }
      // Note that X is defined as forward according to WPILib convention,
      // and Y is defined as to the left according to WPILib convention.
      drivetrain.setDefaultCommand(
          // Drivetrain will execute this command periodically
          drivetrain.applyRequest(() ->
              drive.withVelocityX(-joystick.getLeftY() * MaxSpeed) // Drive forward with negative Y (forward)
                  .withVelocityY(-joystick.getLeftX() * MaxSpeed) // Drive left with negative X (left)
                  .withRotationalRate(-joystick.getRightX() * MaxAngularRate) // Drive counterclockwise with negative X (left)
          )
      );

      //UNIVERSAL BINDS

      //Reset Field Orientation
      joystick.back().onTrue(drivetrain.runOnce(() -> drivetrain.seedFieldCentric()));
      
      //Auto Allign to April Tag
      joystick.leftBumper().onTrue(autoAlign);

      //Use Shooter
      joystick.rightTrigger().whileTrue(shoot());
      joystick.leftTrigger().whileTrue(intake());

      //CALIBRATION MODE BINDS
      if(mode.getSelected() == true){
        System.out.println("CALIBRATING");
        elevator.removeDefaultCommand();
        arm.removeDefaultCommand();

        //Zero Arm and Elevator
        joystick.a().onTrue(calibrateElevator());
        joystick.b().onTrue(calibrateArm());

        //Force stop Arm and Elevator
        joystick.x().onTrue(stopElevator());
        joystick.y().onTrue(stopArm());

        //Move Elevator
        joystick.povUp().whileTrue(elevatorUp());
        joystick.povDown().whileTrue(elevatorDown());

        //Move Arm
        joystick.povLeft().whileTrue(armCounterClockwise());
        joystick.povRight().whileTrue(armClockwise());
        }
      //COMPETITION MODE BINDS
      else{
        System.out.println("COMPETITION");

        //Default State
        joystick.a().onTrue(defaultState());

        //Collect State
        joystick.b().onTrue(collectState());

        //Score Coral
        joystick.povLeft().onTrue(l1State());
        joystick.povDown().onTrue(l2State());
        joystick.povRight().onTrue(l3State());
        joystick.povUp().onTrue(l4State());

        //Use Climber
        joystick.y().whileTrue(climb());
        joystick.x().whileTrue(Unclimb());
        
        elevator.setDefaultCommand(elevator.runElevator());
        arm.setDefaultCommand(arm.runArm());

          }

      drivetrain.registerTelemetry(logger::telemeterize);
    }

     //Moves elevator to different positions, will be revised
  
  public BooleanSupplier armElevatorAtGoal(){
    if(arm.atGoal().getAsBoolean() && elevator.atGoal().getAsBoolean()){
      return ()->true;
    }
    else{
      return ()->false;
    }
  }

  //ELEVATOR COMMANDS
  public Command elevatorTop(){
    return runOnce(()-> {elevator.moveToPosition(20.0);}, elevator);
  }

  public Command elevatorMid(){
    return runOnce(()-> {elevator.moveToPosition(14.0);}, elevator);
  }

  public Command elevatorBottom(){
    return runOnce(()-> {elevator.moveToPosition(1.0);}, elevator);
  }

  public Command elevatorUp(){
    return run(()-> {elevator.setMotor(0.3);}, elevator).finallyDo(()->elevator.stopMotor());
  }

  public Command elevatorDown(){
    return run(()-> {elevator.setMotor(-0.3);}, elevator).finallyDo(()->elevator.stopMotor());
  }

  public Command stopElevator(){
    return run(()-> {elevator.stopMotor();}, elevator);
  }

  //Calibrates the Elevator conversion factor from the bottom. MAKE SURE IT STARTS AT THE BOTTOM
  public Command calibrateElevator(){
    return runOnce(()->elevator.zeroEncoder());
  }

  //ARM COMMANDS
  public Command calibrateArm(){
    return sequence(
      runOnce(() -> {arm.zero();}, arm)
    );
  }

  public Command ArmSide(){
    return runOnce(()->{arm.setPosition(60.0);}, arm);
  }

  public Command ArmUp(){
    return runOnce(()->{arm.setPosition(120.0);},arm);
  }

  public Command armCounterClockwise(){
    return run(()->arm.runMotor(4.0)).finallyDo(()->arm.stop());
  }

  public Command armClockwise(){
    return run(()->arm.runMotor(-4.0)).finallyDo(()->arm.stop());
  }

  public Command stopArm(){
    return runOnce(()->{arm.stop();});
  }

  //INTAKE COMMANDS
  public Command intake(){
    return run(()->{intake.runIntake(-8);}, intake).until(intake.gotCoral()).finallyDo(()->intake.stopIntake());
  }

  public Command shoot(){
    return run(()->{intake.runIntake(8);}, intake).finallyDo(()->intake.stopIntake());
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }

  //Climber Commands
  public Command climb(){
    return run(()->{climber.runClimber(6.0);}).finallyDo(()->climber.stop());
  }

  public Command Unclimb(){
    return run(()->{climber.runClimber(-6.0);}).finallyDo(()->climber.stop());
  }

  //SEQUENCE COMMANDS

  public Command defaultState(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(0.0);})
    ).until(armElevatorAtGoal());
  }

  public Command collectState(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(1.0);})
    ).until(armElevatorAtGoal());

  }

  public Command l4State(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(1.0);})
    ).until(armElevatorAtGoal());
  }

  public Command l3State(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(1.0);})
    ).until(armElevatorAtGoal());
  }

  public Command l2State(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(1.0);})
    ).until(armElevatorAtGoal());
  }

  public Command l1State(){
    return parallel
    (
        runOnce(()->{elevator.moveToPosition(1.0);}),
        runOnce(()->{arm.setPosition(1.0);})
    ).until(armElevatorAtGoal());
  }

  }

