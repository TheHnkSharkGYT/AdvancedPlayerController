/* @Author ManuhGameDev - RigidBody Version with dynamic footsteps */
@AutoWired public SpatialObject player;
@AutoWired public SpatialObject head;
@AutoWired public SpatialObject groundCheck;

public Rigidbody rb;
public float angulo;
public float sensibilidade = 10f;
public float velocidadeBase = 4f;
public float velocidadeCrouch = 0.5f;
public float velocidadeAtual = 4f;
public boolean agachando;
public float altura = 1.8f;
public float alturaAgachado = 0.6f;
public float velocidadeAgachar = 0.2f;
public float jumpForce = 6f;
public float groundDistance = 0.3f;
public SpatialObject Som;

// Sounds
public SoundPlayer crouchEnterSound;
public SoundPlayer crouchExitSound;
public SoundPlayer fallingSound;
public SoundPlayer landSound;

// Sidle system
public String SiddleArea = "";
public float siddleSpeed = 1.2f;
public float siddleInSpeed = 1f;
public float siddleOutSpeed = 1f;
public float idleAnimSpeed = 1f;
public SpatialObject LeftBut, RightBut, CrouchBut, JumpBut, SwitchRedBut, SwitchBlueBut, InventoryBut, FlashlightBut;
public SoundPlayer siddleInSound, siddleOutSound, siddlingSound;
public Animation SiddleIn, SiddleOut;
public boolean isSiddling = false, wasSiddling = false;

// Crouch button & textures
public SUIButton crouchButton;
public Texture standPoseTex, sitPoseTex;

// Animation
public AnimationPlayer AP;
public Animation CrouchIn, CrouchOut, Falling, Land, Walk, WalkToStop, Idle;
public boolean wasMoving = false;
public boolean prevDown = false, lastCrouchState = false, wasGrounded = true;
public Laser laser = new Laser();

// ==================== FOOTSTEP SYSTEM ====================
// Footstep sound prefabs (ObjectFile, each contains a SoundPlayer with Play On Awake)
public ObjectFile footstep1, footstep2, footstep3, footstep4, footstep5, footstep6;
public String floorName = "Ground";               // collider name that triggers footsteps
public float footstepMinDelay = 0.2f;             // fastest step interval (at max speed)
public float footstepMaxDelay = 0.6f;             // slowest step interval (at min speed)
public float footstepMinSpeed = 0.5f;             // speed at which we use maxDelay (slowest steps)
public float footstepMaxSpeed = 6f;               // speed at which we use minDelay (fastest steps)

private float stepTimer = 0f;
// ========================================================

void start() {
    rb = player.getPhysics().getPhysicsEntity();
    velocidadeAtual = velocidadeBase;

    if (AP != null) {
        CrouchIn = AP.getAnimation("CrouchEnter");
        CrouchOut = AP.getAnimation("CrouchExit");
        Falling = AP.getAnimation("Falling");
        Land = AP.getAnimation("Land");
        SiddleIn = AP.getAnimation("Lower");
        SiddleOut = AP.getAnimation("Raise");
        Walk = AP.getAnimation("Walk");  
        WalkToStop = AP.getAnimation("WalkToStop");  
        Idle = AP.getAnimation("Idle");
        if (Idle != null) Idle.speed = idleAnimSpeed;
    }

    if (crouchButton != null && standPoseTex != null) {
        crouchButton.setNormalImage(standPoseTex);
    }
}

void repeat() {
    boolean grounded = isGrounded();

    handleFallSystem(grounded);
    handleSiddleSystem();
    mover();
    handleWalkAnimations();
    rotacionar();
    pular(grounded);
    agachar();
    handleCrouchButton();
    handleCrouchAnimation();
    
    // ----- Dynamic footsteps (only when moving and on correct floor) -----
    updateFootsteps(grounded);
}

// ==================== FOOTSTEP LOGIC ====================
void updateFootsteps(boolean grounded) {
    if (rb == null) return;
    
    // Only play footsteps if grounded, on correct floor, and moving
    boolean onFloor = false;
    try {
        onFloor = rb.colliderWithName(floorName);
    } catch (Throwable t) { }
    if (!grounded || !onFloor) {
        stepTimer = 0f;
        return;
    }
    
    // Check if player is moving (velocity in XZ plane)
    float velXZ = (float) Math.sqrt(rb.getVelocity().getX() * rb.getVelocity().getX() + rb.getVelocity().getZ() * rb.getVelocity().getZ());
    boolean moving = velXZ > 0.2f;  // small threshold to ignore micro‑movements
    if (!moving) {
        stepTimer = 0f;
        return;
    }
    
    // Map current speed to step interval (faster speed = shorter interval)
    float speed = Math.clamp(velXZ, footstepMinSpeed, footstepMaxSpeed);
    float t = (speed - footstepMinSpeed) / (footstepMaxSpeed - footstepMinSpeed); // 0 = slow, 1 = fast
    float currentInterval = footstepMaxDelay - (footstepMaxDelay - footstepMinDelay) * t;
    
    stepTimer += Time.deltaTime();
    if (stepTimer >= currentInterval) {
        playRandomFootstep();
        stepTimer = 0f;
    }
}

void playRandomFootstep() {
    int index = (int) Random.range(1, 7); // 1‑6
    ObjectFile footstep = getFootstepByIndex(index);
    if (footstep != null && player != null) {
        player.instantiate(footstep);
    }
}

ObjectFile getFootstepByIndex(int index) {
    switch (index) {
        case 1: return footstep1;
        case 2: return footstep2;
        case 3: return footstep3;
        case 4: return footstep4;
        case 5: return footstep5;
        case 6: return footstep6;
        default: return null;
    }
}
// ========================================================

// ==================== ORIGINAL METHODS (unchanged) ====================
public void handleSiddleSystem() {
    isSiddling = false;
    if (rb != null && SiddleArea != null) {
        if (rb.colliderWithName(SiddleArea)) isSiddling = true;
    }
    if (isSiddling && !wasSiddling) {
        setButtonsEnabled(false);
        if (SiddleIn != null) { SiddleIn.speed = siddleInSpeed; SiddleIn.play(); }
        if (siddleInSound != null) siddleInSound.play();
    }
    if (!isSiddling && wasSiddling) {
        setButtonsEnabled(true);
        if (SiddleOut != null) { SiddleOut.speed = siddleOutSpeed; SiddleOut.play(); }
        if (siddleOutSound != null) siddleOutSound.play();
        if (siddlingSound != null) siddlingSound.stop();
    }
    float inputY = Input.getAxisValue("joystick").y;
    float inputX = Input.getAxisValue("joystick").x;
    boolean moving = Math.abs(inputX) > 0.05f || Math.abs(inputY) > 0.05f;
    if (isSiddling && moving) {
        if (siddlingSound != null && !siddlingSound.isPlaying()) siddlingSound.play();
    } else {
        if (siddlingSound != null && siddlingSound.isPlaying()) siddlingSound.stop();
    }
    wasSiddling = isSiddling;
}

void setButtonsEnabled(boolean enabled) {
    if (LeftBut != null) LeftBut.setEnabled(enabled);
    if (RightBut != null) RightBut.setEnabled(enabled);
    if (CrouchBut != null) CrouchBut.setEnabled(enabled);
    if (JumpBut != null) JumpBut.setEnabled(enabled);
    if (SwitchRedBut != null) SwitchRedBut.setEnabled(enabled);
    if (SwitchBlueBut != null) SwitchBlueBut.setEnabled(enabled);
    if (InventoryBut != null) InventoryBut.setEnabled(enabled);
    if (FlashlightBut != null) FlashlightBut.setEnabled(enabled);
}

public void handleWalkAnimations() {
    if (isSiddling) {
        if (Walk != null) Walk.stop();
        if (Idle != null) Idle.stop();
        wasMoving = false;
        return;
    }
    float inputY = Input.getAxisValue("joystick").y;
    float inputX = Input.getAxisValue("joystick").x;
    boolean moving = Math.abs(inputX) > 0.05f || Math.abs(inputY) > 0.05f;
    float velocityXZ = (float) Math.sqrt(rb.getVelocity().getX() * rb.getVelocity().getX() + rb.getVelocity().getZ() * rb.getVelocity().getZ());
    float speedFactor = Math.clamp(0.4f, velocityXZ * 1f, 3f);
    if (moving) {
        if (!wasMoving && Idle != null) Idle.stop();
        if (Walk != null) {
            if (!Walk.isPlaying()) Walk.playInLoop();
            Walk.speed = speedFactor;
        }
    } else {
        if (wasMoving) {
            if (Walk != null) Walk.stop();
            if (WalkToStop != null) {
                WalkToStop.play();
                WalkToStop.speed = 2f;
            }
        }
        if (Idle != null && !Idle.isPlaying()) {
            Idle.speed = idleAnimSpeed;
            Idle.playInLoop();
        }
    }
    wasMoving = moving;
}

public boolean isGrounded() {
    try {
        LaserHit hit = laser.trace(groundCheck.getTransform().getGlobalPosition(), Vector3.down(), groundDistance);
        return hit != null;
    } catch (Throwable e) { return false; }
}

public void handleFallSystem(boolean grounded) {
    if (!grounded) {
        if (Falling != null && !Falling.isPlaying()) Falling.playInLoop();
        if (fallingSound != null && !fallingSound.isPlaying()) fallingSound.play();
        wasGrounded = false;
        return;
    }
    if (grounded && !wasGrounded) {
        if (Falling != null) Falling.stop();
        if (fallingSound != null) fallingSound.stop();
        if (Land != null) { Land.play(); Land.speed = 3; }
        if (landSound != null) landSound.play();
        wasGrounded = true;
    }
}

public void mover() {
    float inputY = Input.getAxisValue("joystick").y;
    float inputX = Input.getAxisValue("joystick").x;
    float targetSpeed = velocidadeBase;
    if (agachando) targetSpeed = velocidadeCrouch;
    if (isSiddling) targetSpeed = siddleSpeed;
    velocidadeAtual = Math.lerp(velocidadeAtual, targetSpeed, 0.15f);
    Vector3 forward = player.getTransform().forward();
    Vector3 right = player.getTransform().right();
    Vector3 move = forward.multiply(inputY).add(right.multiply(-inputX)).multiply(velocidadeAtual);
    rb.setVelocity(move.getX(), rb.getVelocity().getY(), move.getZ());
}

public void rotacionar() {
    angulo = Math.clamp(-90, angulo + (Input.getAxisValue("slide").y * sensibilidade) * Time.deltaTime, 90);
    float sin = (float) Math.sin(-angulo);
    float cos = (float) Math.cos(-angulo);
    head.getTransform().getRotation().selfLookTo(new Vector3(0, sin, cos));
    player.getTransform().rotate(0f, (-Input.getAxisValue("slide").x * sensibilidade) * Time.deltaTime, 0f);
}

public void pular(boolean grounded) {
    if (agachando) return;
    if (Input.isKeyDown("jump") && grounded) {
        rb.addVelocity(0, jumpForce, 0);
    }
}

public void agachar() {
    if (agachando) {
        head.getTransform().getPosition().setY(Math.lerp(head.getTransform().getPosition().getY(), alturaAgachado / 2f, velocidadeAgachar));
        if (Som != null) Som.setEnabled(false);
    } else {
        head.getTransform().getPosition().setY(Math.lerp(head.getTransform().getPosition().getY(), altura / 2f, velocidadeAgachar));
        if (Som != null) Som.setEnabled(true);
    }
}

public void handleCrouchAnimation() {
    if (AP == null) return;
    if (agachando != lastCrouchState) {
        if (agachando) {
            if (CrouchIn != null) { CrouchIn.play(); CrouchIn.speed = 3; }
        } else {
            if (CrouchOut != null) { CrouchOut.play(); CrouchOut.speed = 3; }
        }
        lastCrouchState = agachando;
    }
}

public void handleCrouchButton() {
    if (crouchButton == null) return;
    boolean down = false;
    try { down = crouchButton.isDown(); } catch (Throwable t) {}
    if (down && !prevDown) alternarAgachamento();
    prevDown = down;
}

public void alternarAgachamento() {
    agachando = !agachando;
    if (agachando) {
        if (crouchEnterSound != null) crouchEnterSound.play();
        if (crouchButton != null && sitPoseTex != null) crouchButton.setNormalImage(sitPoseTex);
    } else {
        if (crouchExitSound != null) crouchExitSound.play();
        if (crouchButton != null && standPoseTex != null) crouchButton.setNormalImage(standPoseTex);
    }
}