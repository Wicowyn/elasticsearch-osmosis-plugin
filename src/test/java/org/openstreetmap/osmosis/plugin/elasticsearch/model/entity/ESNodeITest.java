package org.openstreetmap.osmosis.plugin.elasticsearch.model.entity;

import java.util.HashMap;

import junit.framework.Assert;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.GeoDistanceFilterBuilder;
import org.elasticsearch.index.query.GeoShapeFilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.junit.Before;
import org.junit.Test;
import org.openstreetmap.osmosis.plugin.elasticsearch.service.IndexAdminService;
import org.openstreetmap.osmosis.plugin.elasticsearch.testutils.AbstractElasticSearchInMemoryTest;

import com.spatial4j.core.distance.DistanceUtils;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.impl.PointImpl;

public class ESNodeITest extends AbstractElasticSearchInMemoryTest {

	private static final String INDEX_NAME = "shape-test";

	private IndexAdminService indexAdminService;

	@Before
	public void setUp() throws Exception {
		indexAdminService = new IndexAdminService(client());
		HashMap<String, String> mappings = new HashMap<String, String>();
		mappings.put(ESEntityType.NODE.getIndiceName(), "{\"properties\":{\"centroid\":{\"type\":\"geo_point\"},\"shape\":{\"type\":\"geo_shape\"}}}");
		indexAdminService.createIndex(INDEX_NAME, 1, 0, mappings);
	}

	@Test
	public void getNode() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();

		// Action
		index(INDEX_NAME, node);
		refresh();

		// Assert
		GetResponse response = client().prepareGet(INDEX_NAME, ESEntityType.NODE.getIndiceName(), "1854801716").execute().actionGet();
		Assert.assertTrue(response.isExists());
		String expected = "{\"centroid\":[2.384955,48.675652],\"shape\":{\"type\":\"point\",\"coordinates\":" +
				"[2.384955,48.675652]},\"tags\":{\"highway\":\"traffic_signals\"}}";
		String actual = response.getSourceAsString();
		Assert.assertEquals(expected, actual);
	}

	@Test
	public void searchNode_withGeoShape() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();
		
		GeoShapeFilterBuilder geoBuilder=FilterBuilders.geoShapeFilter("shape",
				buildSquareShape(48.675652, 2.384955, 20),
				ShapeRelation.INTERSECTS);
		
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(geoBuilder)
				.execute().actionGet();

		// Assert
		Assert.assertEquals(1, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoShape_shouldNotMatch() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();

		// Action
		GeoShapeFilterBuilder geoBuilder=FilterBuilders.geoShapeFilter("shape",
				buildSquareShape(48.676455, 2.380899, 20),
				ShapeRelation.INTERSECTS);
		
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(geoBuilder)
				.execute().actionGet();

		// Assert
		Assert.assertEquals(0, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoShapeAndLargeFilterShape() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();

		// Action
		GeoShapeFilterBuilder geoBuilder=FilterBuilders.geoShapeFilter("shape",
				buildSquareShape(48.675652, 2.384955, 10000),
				ShapeRelation.INTERSECTS);
		
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(geoBuilder)
				.execute().actionGet();

		// Assert
		Assert.assertEquals(1, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoShapeAndCloseFilterShape() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();

		// Action
		// Can't do better than 20m with default shape configuration
		GeoShapeFilterBuilder geoBuilder=FilterBuilders.geoShapeFilter("shape",
				buildSquareShape(48.675652, 2.384955, 20),
				ShapeRelation.INTERSECTS);
		
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(geoBuilder)
				.execute().actionGet();

		// Assert
		Assert.assertEquals(1, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoPoint() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();

		// Action
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(new GeoDistanceFilterBuilder("centroid").point(48.675652, 2.384955).distance(20, DistanceUnit.METERS))
				.execute().actionGet();

		// Assert
		Assert.assertEquals(1, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoPoint_shouldNotMatch() {
		// Setup
		ESNode node = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node);
		refresh();

		// Action
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(new GeoDistanceFilterBuilder("centroid").point(48.676455, 2.380899).distance(20, DistanceUnit.METERS))
				.execute().actionGet();

		// Assert
		Assert.assertEquals(0, searchResponse.getHits().hits().length);
	}

	@Test
	public void searchNode_withGeoPoint_severalPointsPlusSort() {
		// Setup
		ESNode node1 = ESNode.Builder.create().id(1854801716).location(48.675652, 2.384955)
				.addTag("highway", "traffic_signals").build();
		ESNode node2 = ESNode.Builder.create().id(1854801717).location(48.676455, 2.380899)
				.addTag("highway", "traffic_signals").build();
		index(INDEX_NAME, node1, node2);
		refresh();

		// Action
		SearchResponse searchResponse = client().prepareSearch(INDEX_NAME).setTypes(ESEntityType.NODE.getIndiceName())
				.setQuery(QueryBuilders.matchAllQuery())
				.setPostFilter(new GeoDistanceFilterBuilder("centroid").point(48.676455, 2.380899).distance(1, DistanceUnit.KILOMETERS))
				.addSort(new GeoDistanceSortBuilder("centroid").point(48.676455, 2.380899).unit(DistanceUnit.METERS))
				.execute().actionGet();

		// Assert
		Assert.assertEquals(2, searchResponse.getHits().hits().length);
		Assert.assertEquals("1854801717", searchResponse.getHits().getAt(0).getId());
		Assert.assertEquals("1854801716", searchResponse.getHits().getAt(1).getId());
	}

	protected ShapeBuilder buildSquareShape(double centerLat, double centerLon, double distanceMeter) {
		Point point = new PointImpl(centerLon, centerLat, ShapeBuilder.SPATIAL_CONTEXT);
		double radius = DistanceUtils.dist2Degrees(distanceMeter / 10E3, DistanceUtils.EARTH_MEAN_RADIUS_KM);
		Rectangle rec=ShapeBuilder.SPATIAL_CONTEXT.makeCircle(point, radius).getBoundingBox();
		
		return ShapeBuilder.newPolygon()
				.point(rec.getMinX(), rec.getMinY())
				.point(rec.getMinX(), rec.getMaxY())
				.point(rec.getMaxX(), rec.getMinY())
				.point(rec.getMaxX(), rec.getMaxY());		
	}

}
