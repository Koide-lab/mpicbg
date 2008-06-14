import ij.IJ;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import mpicbg.models.AffineModel2D;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2D;


public class TransformMesh
{
	final HashMap< AffineModel2D, ArrayList< PointMatch > > a = new HashMap< AffineModel2D, ArrayList< PointMatch > >();
	final HashMap< PointMatch, ArrayList< AffineModel2D > > l = new HashMap< PointMatch, ArrayList< AffineModel2D > >();
	
	/**
	 * Add a triangle defined by 3 PointMatches that defines an
	 * AffineTransform2D.
	 * 
	 * @param t 3 PointMatches (will not be copied, so do not reuse this list!)
	 */
	public void addTriangle( ArrayList< PointMatch > t )
	{
		AffineModel2D m = new AffineModel2D();
		try
		{
			m.fit( t );
		}
		catch ( NotEnoughDataPointsException e )
		{
			IJ.error( e.getMessage() );
			e.printStackTrace( System.err );
		}
		a.put( m, t );
		
		for ( PointMatch pm : t )
		{
			if ( !l.containsKey( pm ) )
				l.put( pm, new ArrayList< AffineModel2D >() );
			l.get( pm ).add( m );
		}
	}
	
	public void init( int numX, int numY, float width, float height )
	{
		PointMatch[] pq = new PointMatch[ numX * numY + ( numX - 1 ) * ( numY - 1 ) ];
		
		float dy = height / ( numY - 1 );
		float dx = width / ( numX - 1 );
		
		int i = 0;
		for ( int xi = 0; xi < numX; ++xi )
		{
			float xip = xi * dx;
			Point p = new Point( new float[]{ xip, 0 } );
			pq[ i ]  = new PointMatch( p, p.clone() );
			
			++i;
		}
		for ( int yi = 1; yi < numY; ++yi )
		{
			// odd row
			float yip = yi * dy - dy / 2;
			for ( int xi = 1; xi < numX; ++xi )
			{
				float xip = xi * dx - dx / 2;
				
				Point p  = new Point( new float[]{ xip, yip } );
				pq[ i ] = new PointMatch( p, p.clone() );
				
				int i1 = i - numX;
				int i2 = i1 + 1;
				
				ArrayList< PointMatch > t1 = new ArrayList< PointMatch >();
				t1.add( pq[ i1 ] );
				t1.add( pq[ i2 ] );
				t1.add( pq[ i ] );
				
				addTriangle( t1 );
				
				++i;
			}
			
			// even row
			yip = yi * dy;
			Point p  = new Point( new float[]{ 0, yip } );
			pq[ i ] = new PointMatch( p, p.clone() );
			
			++i;
			
			for ( int xi = 1; xi < numX; ++xi )
			{
				float xip = xi * dx;
								
				p = new Point( new float[]{ xip, yip } );
				pq[ i ] = new PointMatch( p, p.clone() );
				
				int i1 = i - 2 * numX;
				int i2 = i1 + 1;
				int i3 = i1 + numX;
				int i4 = i - 1;
				
				ArrayList< PointMatch > t1 = new ArrayList< PointMatch >();
				t1.add( pq[ i1 ] );
				t1.add( pq[ i3 ] );
				t1.add( pq[ i4 ] );
				
				ArrayList< PointMatch > t2 = new ArrayList< PointMatch >();
				t2.add( pq[ i4 ] );
				t2.add( pq[ i3 ] );
				t2.add( pq[ i ] );
				
				ArrayList< PointMatch > t3 = new ArrayList< PointMatch >();
				t3.add( pq[ i ] );
				t3.add( pq[ i3 ] );
				t3.add( pq[ i2 ] );
				
				addTriangle( t1 );
				addTriangle( t2 );
				addTriangle( t3 );
				
				++i;
			}
		}
	}
	
	
	/**
	 * 
	 * @param pm PointMatches
	 * @return bounding box with
	 *   min(x,y) = box[0][0],box[0][1] and 
	 *   max(x,y) = box[1][0],box[1][1]
	 */
	protected float[][] getBoundingBox( ArrayList< PointMatch > pm )
	{
		float[] first = pm.get( 0 ).getP1().getW();
		float[][] box = new float[ 2 ][];
		box[ 0 ] = first.clone();
		box[ 1 ] = first.clone();
		
		for ( PointMatch p : pm )
		{
			float[] t = p.getP2().getW();
			if ( t[ 0 ] < box[ 0 ][ 0 ] ) box[ 0 ][ 0 ] = t[ 0 ];
			else if ( t[ 0 ] > box[ 1 ][ 0 ] ) box[ 1 ][ 0 ] = t[ 0 ];
			if ( t[ 1 ] < box[ 0 ][ 1 ] ) box[ 0 ][ 1 ] = t[ 1 ];
			else if ( t[ 1 ] > box[ 1 ][ 1 ] ) box[ 1 ][ 1 ] = t[ 1 ];
		}
		
		return box;
	}
	
	protected void apply( AffineModel2D ai, ImageProcessor src, ImageProcessor trg )
	{
		ArrayList< PointMatch > pm = a.get( ai );
		float[][] box = getBoundingBox( pm );
		for ( int y = ( int )box[ 0 ][ 1 ]; y <= ( int )box[ 1 ][ 1 ]; ++y )
		{
X:			for ( int x = ( int )box[ 0 ][ 0 ]; x <= ( int )box[ 1 ][ 0 ]; ++x )
			{
				for ( int j = 0; j < pm.size(); ++j )
				{
					PointMatch r1 = pm.get( j );
					PointMatch r2 = pm.get( ( j + 1 ) % pm.size() );
					float[] t1 = r1.getP2().getW();
					float[] t2 = r2.getP2().getW();
					
					float x1 = t2[ 0 ] - t1[ 0 ];
					float y1 = t2[ 1 ] - t1[ 1 ];
					float x2 = ( float )x - t1[ 0 ];
					float y2 = ( float )y - t1[ 1 ];
					
					if ( x1 * y2 - y1 * x2 < 0 ) continue X;
				}
				float[] t = new float[]{ x, y };
				try
				{
					ai.applyInverseInPlace( t );
				}
				catch ( Exception e )
				{
					e.printStackTrace( System.err );
				}
				trg.putPixel( x, y, src.getPixel( ( int )t[ 0 ], ( int )t[ 1 ] ) );
			}
		}
	}
	
	public void apply( ImageProcessor src, ImageProcessor trg )
	{
		trg.setColor( Color.black );
		trg.fill();
		Set< AffineModel2D > s = a.keySet();
		for ( AffineModel2D ai : s )
			apply( ai, src, trg );
	}
	
	private void illustrateTriangle( AffineModel2D ai, GeneralPath path )
	{
		ArrayList< PointMatch > m = a.get( ai );
		
		float[] w = m.get( 0 ).getP2().getW();
		path.moveTo( w[ 0 ], w[ 1 ] );
		
		for ( int i = 1; i < m.size(); ++i )
		{
			w = m.get( i ).getP2().getW();
			path.lineTo( w[ 0 ], w[ 1 ] );
		}
		path.closePath();
	}
	
	/**
	 * Create a Shape that illustrates the mesh.
	 * 
	 * @return the illustration
	 */
	public Shape illustrateMesh()
	{
		GeneralPath path = new GeneralPath();
		
		Set< AffineModel2D > s = a.keySet();
		for ( AffineModel2D ai : s )
			illustrateTriangle( ai, path );
		
		return path;
	}
	
	public void update( PointMatch p )
	{
		for ( AffineModel2D ai : l.get( p ) )
		{
			try
			{
				ai.fit( a.get( ai ) );
			}
			catch ( NotEnoughDataPointsException e )
			{
				IJ.error( e.getMessage() );
				e.printStackTrace( System.err );
			}
		}
	}
	
	public void clear()
	{
		a.clear();
		l.clear();
	}
}