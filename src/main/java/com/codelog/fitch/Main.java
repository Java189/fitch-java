/*

A platformer game written using OpenGL.
    Copyright (C) 2017-2019  Jaco Malan

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package com.codelog.fitch;

import com.codelog.fitch.game.Block;
import com.codelog.fitch.game.Level;
import com.codelog.fitch.game.LevelParseException;
import com.codelog.fitch.game.Player;
import com.codelog.fitch.graphics.Rectangle;
import com.codelog.fitch.graphics.*;
import com.codelog.fitch.logging.LogSeverity;
import com.codelog.fitch.logging.Logger;
import com.codelog.fitch.math.Vector2;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.util.Animator;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class Main implements KeyListener, GLEventListener {

    // Constants
    private static final Vector2 GRAVITY = new Vector2(0, 0.8);

    // Instance variables
    private GLWindow window;
    private Animator animator;
    private Player player;
    private List<Drawable> drawList;
    private Map<String, Texture2D> textureMap;
    private long lastTime;
    private long minPeriod = (long)(1f / 60f * 100 * 10000000f); // Minimum time in nanoseconds between updates.

    // Static variables
    private static boolean write_log = true;
    private static Logger logger;
    private static Rectangle background;
    private static Mesh levelMesh;
    private static Level level;
    private static HashMap<String, String> shaderSources;
    public static World world;
    public static double unitScalingFactor = 100;
    public static int WIDTH = 800;
    public static int HEIGHT = 600;

    private static String[] propsToLog = new String[] {
            "os.name", "os.arch", "os.version", "java.vendor", "java.vm.name", "java.version"
    };

    public static Logger getLogger() { return logger; }

    private static void sendHelp() {

        // Shows commandline help message.
        String sb = "Usage: fitch <arguments>\n\n" +
                    "\t--no-log\tDo not write log info to a file.\n";
        System.out.println(sb);

    }

    public static void main(String[] args) throws IllegalArgumentException {

        // Parse commandline arguments.
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {

                    case "--no-log":
                        write_log = false;
                        break;

                    default:
                        sendHelp();
                        break;

                } // endswitch
            } // endfor
        } // endif

        // Start game.
        logger = new Logger();
        new Main().setup();

    }
    private void setup() {

        // Setup GL Canvas and capabilities.
        GLProfile glProfile = GLProfile.get(GLProfile.GL4);
        GLCapabilities glCap = new GLCapabilities(glProfile);
        glCap.setDepthBits(16);

        window = GLWindow.create(glCap);
        animator = new Animator(window);

        window.addGLEventListener(this);

        //int width = Toolkit.getDefaultToolkit().getScreenSize().width;
        //int height = Toolkit.getDefaultToolkit().getScreenSize().height;
        //window.setSize(width, height);
        window.setTitle("Fitch");
        window.setSize(WIDTH, HEIGHT);


        window.setVisible(true);
        window.addKeyListener(this);
        window.setDefaultCloseOperation(GLWindow.WindowClosingMode.DISPOSE_ON_CLOSE);

        window.setAutoSwapBufferMode(false);
        animator.start();

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDestroyNotify(com.jogamp.newt.event.WindowEvent e) {
                animator.stop();
                if (write_log)
                    logger.write();
            }
        });

    }

    @Override
    public void init(GLAutoDrawable drawable) {

        logSystemInfo();

        var gl = drawable.getGL().getGL4();

        logger.log(this, LogSeverity.INFO, String.format("OpenGL version: %s\n", window.getContext().getGLVersion()));

        gl.glDebugMessageControl(gl.GL_DONT_CARE, gl.GL_DONT_CARE, gl.GL_DONT_CARE, 0, null, 0, true);

        // Enable OpenGL features
        gl.glEnable(gl.GL_DEPTH_TEST);
        gl.glEnable(gl.GL_BLEND);
        gl.glEnable(gl.GL_TEXTURE_2D);

        // Set GL functions
        gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthFunc(gl.GL_LEQUAL);

        drawList = new ArrayList<>();
        textureMap = new HashMap<>();

        // Initialize textures
        try {
            textureMap.put("player", Texture2D.loadTexture(gl, "player.png"));
            textureMap.put("background", Texture2D.loadTexture(gl, "background.png"));
            textureMap.put("solid", Texture2D.loadTexture(gl, "solid.png"));
        } catch (IOException e) {
            logger.log(this, e);
        }

        // Setup background
        background = new Rectangle(-200, -200, window.getWidth() + 200, window.getHeight() + 200);
        background.setDrawDepth(0.9f);
        background.setUseTexture(true);
        drawList.add(background);

        Vec2 grav = new Vec2((float)GRAVITY.x, (float)GRAVITY.y);
        world = new World(grav);

        // TODO: Implement level loading.

        try {
            level = Tools.loadLevel("content/level1.fl");
        } catch (IOException | LevelParseException e) {
            // We can't recover from level-load failure, so exit.
            logger.log(this, e);
            window.sendWindowEvent(WindowEvent.EVENT_WINDOW_DESTROY_NOTIFY);
        }

        // Init shaders
        shaderSources = new HashMap<>();
        shaderSources.put("block_v", "shaders/bvshader_tex.glsl");
        shaderSources.put("block_f", "shaders/bfshader_tex.glsl");

        MeshBuilder mb = new MeshBuilder();
        mb.addVertexShader(shaderSources.get("block_v"));
        mb.addFragmentShader(shaderSources.get("block_f"));

        for (Block b : level.getBlocks()) {
            b.getDrawRect().setUseTexture(true);
            b.getDrawRect().setDrawDepth(0.2f);
            drawList.add(b.getDrawRect());
        }

        levelMesh = mb.toMesh();
        //drawList.add(levelMesh);

        // Setup player
        var pwidth = 50;
        var pheight = 100;
        var pos = level.getStartPos().sub(new Vector2(0, pheight * 2));
        player = new Player(pos, pwidth, pheight);
        player.setDrawDepth(0.0f);
        drawList.add(player);

        // Initialize all the drawables
        for (Drawable d : drawList)
            d.init(gl);

        // Set initial textures
        player.setTexture(textureMap.get("player"), true);
        background.setTexture(textureMap.get("background"), false);

        for (Block b : level.getBlocks()) {
            b.getDrawRect().setTexture(textureMap.get("solid"), false);
        }

        logger.log(this, LogSeverity.INFO, "Initialising...");

        System.nanoTime();
        update(gl);

    }

    private void logSystemInfo() {

        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("System information:");
        msgBuilder.append('\n');

        for (String prop : propsToLog) {
            msgBuilder.append('\t');
            msgBuilder.append(prop);
            msgBuilder.append('=');
            msgBuilder.append(System.getProperty(prop));
            msgBuilder.append('\n');
        }

        logger.log(this, LogSeverity.INFO, msgBuilder.toString());

    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        // Nothing to dispose of :)
    }

    @Override
    public void display(GLAutoDrawable drawable) { // Called on render

        var gl = drawable.getGL().getGL4();

        // Pre-update
        float[] cfb = Colour.CornFlowerBlue.getFloats();
        gl.glClearColor(cfb[0], cfb[1], cfb[2], cfb[3]);
        gl.glClearDepth(1f);
        gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT);

        long time = System.nanoTime();
        long deltaTime = time - lastTime;

        if (deltaTime >= minPeriod) {
            update(gl);
            lastTime = time;
        }

        render(gl); // Render everything

        // Post-render
        // TODO: Add post-render cleanup.

        window.swapBuffers();

    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

        var gl = drawable.getGL().getGL4();

        // Resize the viewport to correspond
        // to the new canvas size.
        gl.glViewport(x, y, width, height);

    }

    private void update(GL4 gl) {

        for (Drawable d : drawList)
            d.update(gl);

        world.step(1f / 10f, 30, 30);

    }

    private void render(GL4 gl) {

        // TODO: Optimize rendering.
        // TODO: Fix translation accuracy

        Matrix4 mat = new Matrix4();
        mat.makeOrtho(0, WIDTH, HEIGHT, 0, 0f, 1f);

        Vector2 pos = player.getPos();
        double translateX = Math.floor(-pos.x - player.getWidth() / 2 + (double)WIDTH / 2);
        double translateY = Math.floor(-pos.y - player.getHeight() / 2 + (double)HEIGHT / 2);

        Matrix4 transMat = new Matrix4();
        transMat.translate((float)translateX, (float)translateY, 0f);

        MatrixStack<Matrix4> stack = new MatrixStack<>();
        stack.push(transMat);
        stack.push(mat);

        background.setPos(new Vector2(-translateX, -translateY));

        background.loadMatrixStack(stack.cloneStack());
        player.loadMatrixStack(stack.cloneStack());

        for (Block b : level.getBlocks()) {
            b.getDrawRect().loadMatrixStack(stack);
        }

        for (Drawable d : drawList)
            d.draw(gl);

    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {

        // TODO: Optimize keyboard handling.

        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
                window.sendWindowEvent(WindowEvent.EVENT_WINDOW_DESTROY_NOTIFY);
                break;
            case KeyEvent.VK_F11:
                break;
            case KeyEvent.VK_SPACE:
                player.getBody().applyForceToCenter(new Vec2(0, -10));
                break;
            case KeyEvent.VK_D:
                player.getBody().applyForceToCenter(new Vec2(2, 0));
                break;
            case KeyEvent.VK_A:
                player.getBody().applyForceToCenter(new Vec2(-2, 0));
                break;
            default:
                break;
        }

    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {

    }

    /**
     * Converts pixel coordinates to Box2D coords.
     * @param pixelCoord Pixel-space coords.
     */
    public static Vec2 pixelsToWorld(Vector2 pixelCoord) {

        double wx =  (WIDTH / 2f + pixelCoord.x) / unitScalingFactor;
        double wy = (HEIGHT / 2f + pixelCoord.y) / unitScalingFactor;

        return new Vec2((float)wx, (float)wy);

    }

    /**
     * Converts Box2D coords to pixel coords.
     * @param worldCoord World-space coords
     */
    public static Vector2 worldToPixels(Vec2 worldCoord) {

        double px = worldCoord.x * unitScalingFactor - WIDTH / 2f;
        double py = worldCoord.y * unitScalingFactor - HEIGHT / 2f;

        return new Vector2(px, py);

    }

    /**
     * Converts scalars from pixel-space to Box2D-space.
     * @param scalar Scalar to convert
     */
    public static double scalarPToW(double scalar) { return scalar / unitScalingFactor; }

    /**
     * Converts scalars from Box2D-space to pixel-space.
     * @param scalar Scalar to convert
     */
    public static double scalarWToP(double scalar) { return scalar * unitScalingFactor; }

}
