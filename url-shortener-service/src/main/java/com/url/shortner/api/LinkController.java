
package com.url.shortner.api;

import com.url.shortner.dto.CreateRequest;
import com.url.shortner.dto.CreateResponse;
import com.url.shortner.dto.Link;
import com.url.shortner.dto.StatsResponse;
import com.url.shortner.service.LinkService;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/shortener-url")
@Tag(
        name = "URL Shortener",
        description = "APIs for creating shortened URLs, redirecting and tracking statistics"
)
public class LinkController {

    private final LinkService service;

    public LinkController(LinkService service) {
        this.service = service;
    }

    @Operation(
            summary = "Create a short URL",
            description = "Creates a shortened URL from the supplied original URL.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Short URL created successfully",
                            content = @Content(
                                    schema = @Schema(implementation = CreateResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid URL or validation error"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error"
                    )
            }
    )
    @PostMapping("/links")
    public ResponseEntity<CreateResponse> create(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Original URL to shorten",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateRequest.class),
                            examples = @ExampleObject(
                                    name = "Google URL",
                                    value = """
                            {
                              "url": "https://www.google.com"
                            }
                            """
                            )
                    )
            )
            @RequestBody CreateRequest request) {

        Link link = service.create(request.url());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(
                        link.getCode(),
                        service.shortUrl(link.getCode())
                ));
    }

    @Operation(
            summary = "Redirect to original URL",
            description = "Resolves the short code and redirects the client to the original URL.",
            responses = {
                    @ApiResponse(
                            responseCode = "302",
                            description = "Redirect to original URL"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Short URL not found"
                    )
            }
    )
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(
            @Parameter(
                    description = "Unique short URL code",
                    example = "aB12Cd",
                    required = true
            )
            @PathVariable("code") String code) {

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(service.resolve(code)))
                .build();
    }

    @Operation(
            summary = "Get URL statistics",
            description = "Returns the original URL and total click count for a short URL.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Statistics retrieved successfully",
                            content = @Content(
                                    schema = @Schema(implementation = StatsResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Short URL not found"
                    )
            }
    )
    @GetMapping("/{code}/stats")
    public StatsResponse stats(
            @Parameter(
                    description = "Unique short URL code",
                    example = "aB12Cd",
                    required = true
            )
            @PathVariable("code") String code) {

        Link link = service.find(code);

        return new StatsResponse(
                link.getCode(),
                service.originalUrl(link),
                link.getClicks()
        );
    }
}
