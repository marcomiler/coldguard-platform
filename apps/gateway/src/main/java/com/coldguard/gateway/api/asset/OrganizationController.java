package com.coldguard.gateway.api.asset;

import com.coldguard.gateway.infrastructure.AssetGrpcClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Organizations and their sites. Shape validation, mapping and the call; no business rule. */
@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController {

  private final AssetGrpcClient asset;

  OrganizationController(AssetGrpcClient asset) {
    this.asset = asset;
  }

  @GetMapping
  PageResponse<OrganizationResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "0") int size) {
    var reply = asset.listOrganizations(AssetRestMapper.listOrganizations(page, size));
    return new PageResponse<>(
        reply.getOrganizationsList().stream().map(AssetRestMapper::toRest).toList(),
        AssetRestMapper.toRest(reply.getPage()));
  }

  @PostMapping
  ResponseEntity<OrganizationResponse> create(
      @Valid @RequestBody CreateOrganizationRequest request) {
    return ResponseEntity.status(201)
        .body(
            AssetRestMapper.toRest(
                asset.createOrganization(AssetRestMapper.organization(request))));
  }

  @GetMapping("/{organizationId}/sites")
  PageResponse<SiteResponse> listSites(
      @PathVariable String organizationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int size) {
    var reply = asset.listSites(AssetRestMapper.listSites(organizationId, page, size));
    return new PageResponse<>(
        reply.getSitesList().stream().map(AssetRestMapper::toRest).toList(),
        AssetRestMapper.toRest(reply.getPage()));
  }

  @PostMapping("/{organizationId}/sites")
  ResponseEntity<SiteResponse> createSite(
      @PathVariable String organizationId, @Valid @RequestBody CreateSiteRequest request) {
    return ResponseEntity.status(201)
        .body(
            AssetRestMapper.toRest(
                asset.createSite(AssetRestMapper.site(organizationId, request))));
  }
}
