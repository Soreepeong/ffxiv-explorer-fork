package ca.fraggergames.ffxivextract.gui.components;

import java.io.IOException;
import java.nio.FloatBuffer;

import javax.media.opengl.GL3;
import javax.media.opengl.GL3bc;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;

import ca.fraggergames.ffxivextract.helpers.Matrix;
import ca.fraggergames.ffxivextract.models.Model;
import ca.fraggergames.ffxivextract.shaders.BlurShader;
import ca.fraggergames.ffxivextract.shaders.DefaultShader;
import ca.fraggergames.ffxivextract.shaders.FXAAShader;

import com.jogamp.common.nio.Buffers;

class ModelRenderer implements GLEventListener{

	private Model model, model2;
	private GLU glu;
	private float zoom = -7;
	private float panX = 0;
	private float panY = 0;
	private float angleX = 0;
	private float angleY = 0;
	
	DefaultShader defaultShader;
	FXAAShader fxaaShader;
	BlurShader blurShader;
	
	static int currentLoD = 0;	
	
	private int[] textureIds;		
	
	//Matrices
	float[] modelMatrix = new float[16];
	float[] viewMatrix = new float[16];
	float[] projMatrix = new float[16];
	
	//Frame Buffer
	int rboId[] = new int[2];
	int fboId[] = new int[2];
	int fboTexture[] = new int[2];
	int canvasWidth, canvasHeight;
	
	//Frame Buffer Quad
	FloatBuffer drawQuad;
	
	public ModelRenderer(Model model, Model model2) {
		this.model = model;
		this.model2 = model2;
		textureIds = new int[model.getNumMaterials() * 4];
		
		drawQuad = Buffers.newDirectFloatBuffer(new float[]{-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f});
	}

	public void resetMaterial() {
		loaded = false;
	}

	public void zoom(int notches) {
		zoom += notches * 0.25f;
	}
	
	public void rotate(float x, float y)
	{
		angleX += x * 1.0f;
		angleY += y * 1.0f;
	}
	
	public void pan(float x, float y)
	{
		panX += x * 0.05f;
		panY += -y * 0.05f;
	}
	
	public void setLoD(int level)
	{
		currentLoD = level;
	}

	boolean loaded = false;		
	
	@Override
	public void display(GLAutoDrawable drawable) {
		GL3bc gl = drawable.getGL().getGL3bc();
		
		if (!loaded)
		{
			model.loadToVRAM(gl);
		//	model2.loadToVRAM(gl);
			loaded = true;
		}
		
	    gl.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT); 		  
	    
	    Matrix.setIdentityM(viewMatrix, 0);
	    Matrix.setIdentityM(modelMatrix, 0);
	    Matrix.translateM(modelMatrix, 0, panX, panY, zoom);
	    Matrix.rotateM(modelMatrix, 0, angleX, 0, 1, 0);
	    Matrix.rotateM(modelMatrix, 0, angleY, 1, 0, 0);		     		   		    		    		    
	   
	    gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fboId[1]);
	    gl.glViewport(0,0, canvasWidth, canvasHeight);		    
	    gl.glClear(GL3.GL_COLOR_BUFFER_BIT | GL3.GL_DEPTH_BUFFER_BIT);
	    			    
	    model.render(defaultShader, viewMatrix, modelMatrix, projMatrix, gl, currentLoD);
	    if (model2 != null)
	    	model2.render(defaultShader, viewMatrix, modelMatrix, projMatrix, gl, currentLoD);		  		   		    		    
	    		    		   
	    //FXAA
	    gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
	    gl.glViewport(0,0, canvasWidth, canvasHeight);
	    gl.glUseProgram(fxaaShader.getShaderProgramID());
	    fxaaShader.setTexture(gl, fboTexture[1]);
	    gl.glVertexAttribPointer(fxaaShader.getAttribPosition(), 2, GL3.GL_FLOAT, false, 0, drawQuad);
		gl.glEnableVertexAttribArray(fxaaShader.getAttribPosition());		    
	    gl.glDrawArrays(GL3.GL_TRIANGLES, 0, 6);
	    gl.glDisableVertexAttribArray(fxaaShader.getAttribPosition());
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GL3 gl = drawable.getGL().getGL3();      // get the OpenGL graphics context
	      glu = new GLU();                         // get GL Utilities
	      gl.glClearColor(0.3f, 0.3f, 0.3f, 0.3f); // set background (clear) color
	      gl.glClearDepth(1.0f);      // set clear depth value to farthest
	      gl.glEnable(GL3.GL_DEPTH_TEST); // enables depth testing
	      gl.glDepthFunc(GL3.GL_LEQUAL);  // the type of depth test to do
	      gl.glEnable(GL3.GL_BLEND);
	      gl.glEnable(GL3.GL_CULL_FACE);
	      gl.glBlendFunc (GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA);	
	      gl.glEnable(GL3.GL_TEXTURE_2D);

	      try {
			defaultShader = new DefaultShader(gl);
			fxaaShader = new FXAAShader(gl);
			blurShader = new BlurShader(gl);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height){
			GL3bc gl = drawable.getGL().getGL3bc();  // get the OpenGL 2 graphics context
		 
	      if (height == 0) height = 1;   // prevent divide by zero
	      float aspect = (float)width / height;
	 
	      canvasWidth = width;
	      canvasHeight = height;
	      
	      gl.glViewport(0, 0, width, height);
	 
	      deleteFrameBuffers(gl);
	      genFrameBuffers(gl);
	      initFrameBuffer(gl, 0, width, height);
	      initFrameBuffer(gl, 1, width, height);
	      
	      Matrix.setIdentityM(projMatrix, 0);
	      Matrix.perspectiveM(projMatrix, 0, 45.0f, aspect, 0.1f, 100.0f);		     
	      Matrix.setIdentityM(modelMatrix, 0);
	      Matrix.setIdentityM(viewMatrix, 0);
	}
	
	private void deleteFrameBuffers(GL3 gl)
	{
		gl.glDeleteTextures(fboTexture.length, fboTexture, 0);
		gl.glDeleteFramebuffers(fboId.length, fboId, 0);
		gl.glDeleteRenderbuffers(rboId.length, rboId, 0);
	}
	
	private void genFrameBuffers(GL3 gl)
	{			
	
	    gl.glGenTextures(fboTexture.length, fboTexture,0);		    
	    gl.glGenFramebuffers(fboId.length, fboId, 0);		    
	    gl.glGenRenderbuffers(rboId.length, rboId, 0);
	}
	
	private void initFrameBuffer(GL3 gl, int id, int width, int height)
	{ 				
	    gl.glBindTexture(GL3.GL_TEXTURE_2D, fboTexture[id]);
	    
	    gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_WRAP_S, GL3.GL_CLAMP_TO_EDGE);
	    gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_WRAP_T, GL3.GL_CLAMP_TO_EDGE);
	    gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
	    gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
	    
	    gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0,GL3.GL_RGBA, width, height, 0,GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, null);		     		    		    
	 
	    //Init FBO/RBO		    
	    gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fboId[id]);
	    gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, rboId[id]);
	    gl.glRenderbufferStorage(GL3.GL_RENDERBUFFER, GL3.GL_DEPTH_COMPONENT, width, height);
	    		    
	    gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_TEXTURE_2D, fboTexture[id], 0);
	    gl.glFramebufferRenderbuffer(GL3.GL_FRAMEBUFFER, GL3.GL_DEPTH_ATTACHMENT, GL3.GL_RENDERBUFFER, rboId[id]); 
	    
	    gl.glBindTexture(GL3.GL_TEXTURE_2D, 0);
	    gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
	    gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, 0);
	    
	    if(gl.glCheckFramebufferStatus(GL3.GL_FRAMEBUFFER) != GL3.GL_FRAMEBUFFER_COMPLETE)
	    	System.out.println("Error creating framebuffer!");
	}
	
}
